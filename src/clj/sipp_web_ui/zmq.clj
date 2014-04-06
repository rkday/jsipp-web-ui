(ns sipp-web-ui.zmq
  (:require [zeromq.zmq :as zmq]
            [clojure.core.async :as a]))

(def zc (zmq/context))

(defn parse [zmq-str]
  (-> zmq-str
      (clojure.string/replace-first "SIPP-" "")
      (clojure.string/split #":")))

(def rtcp-calls (atom {}))

(defn rtcp-consumer [results [msg-type timestamp id jitter packets-seen packets-lost packets-out-of-sequence rtt :as data]]
  (swap! rtcp-calls assoc id {:jitter (Double/parseDouble jitter)
                              :packets-seen (Long/parseLong packets-seen)
                              :packets-lost (Double/parseDouble packets-lost)
                              :packets-out-of-sequence (Double/parseDouble packets-out-of-sequence)
                              :rtt (Double/parseDouble rtt)}))

(defn consumer [results [msg-type timestamp scenario-name call-number call-id index actual-recvd :as msg]]
  (condp = msg-type
    "RTCP" (rtcp-consumer results msg)
    "PHASE_SUCCESS" (swap! results update-in [:scenario  (Integer/parseInt index) :msg] inc)
    "RECV_TIMED_OUT" (swap! results update-in [:scenario (Integer/parseInt index) :timedout] inc)
    "CALL_SUCCESS" (swap! results update-in [:successful-calls] inc)
    "CALL_FAILURE" (swap! results update-in [:failed-calls] inc)
    "CALL_BEGIN" nil))

(defn poll-socket [sock chan]
  (a/go
   (loop []
     (a/>! chan (parse (zmq/receive-str sock)))
     (recur))))

(defn scenario-definition []
  (let [z-req    (->
         (zmq/socket zc :req)
         (zmq/connect "tcp://127.0.0.1:5557"))
        _ (zmq/send-str z-req "get scenario")
        scenario-string (zmq/receive-str z-req)
        scenario-elements (map #(clojure.string/split % #":") (clojure.string/split scenario-string #";"))]
    (mapv (fn [[phase details]] {:phase phase :details details :msg 0 :timedout 0})
          ; (drop 1) because the first pair is the scenario name
          (drop 1 scenario-elements))))

(defn rtcp-reduce [rtcp-calls]
  (let [{:keys [total-jitter hwm-jitter total-packets-lost total-packets-out-of-sequence count]}
        (reduce
         (fn [final [_ {:keys [jitter packets-lost packets-out-of-sequence] :as c}]]
           (-> final
               (update-in [:total-jitter] #(+ % jitter))
               (update-in [:total-packets-lost] #(+ % packets-lost))
               (update-in [:total-packets-out-of-sequence] #(+ % packets-out-of-sequence))
               (update-in [:hwm-jitter] #(max % jitter))
               (update-in [:count] inc)))
         {:total-jitter 0
          :hwm-jitter 0
          :total-packets-lost 0
          :total-packets-out-of-sequence 0
          :count 0}
         rtcp-calls)
        safe-count (max 1 count)]
    {:avg-jitter (/ total-jitter safe-count)
     :hwm-jitter hwm-jitter
     :avg-packets-lost (/ total-packets-lost safe-count)
     :avg-packets-out-of-sequence (/ total-packets-out-of-sequence safe-count)}))


(defn run []
  (let [zs (->
            (zmq/socket zc :sub)
            (zmq/connect "tcp://127.0.0.1:5556")
            (zmq/subscribe "SIPP"))
        events-channel (a/chan)
        start-results {:rtcp {:avg-jitter 0
                              :hwm-jitter 0
                              :avg-packets-lost 0
                              :avg-packets-out-of-sequence 0}
                       :successful-calls 0
                       :failed-calls 0
                       :scenario
                       (scenario-definition)}
        results  (atom start-results)]
    (poll-socket zs events-channel)
    (let [retchan (a/chan 1024)]
      (a/go
       (loop [timeout-chan (a/timeout 1000)]
         (let [[val port] (a/alts!! [events-channel timeout-chan])]
           (if (nil? val)
             (do (a/>! retchan (-> @results
                                   (assoc :rtcp (rtcp-reduce @rtcp-calls))
                                   (assoc :timestamp (System/currentTimeMillis))))
                 (reset! results start-results)
                 (reset! rtcp-calls {})
                 (recur (a/timeout 1000)))
             (do (consumer results val)
                 (recur timeout-chan))))))
      retchan))
)
