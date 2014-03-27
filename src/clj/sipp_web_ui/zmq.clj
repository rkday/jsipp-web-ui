(ns sipp-web-ui.zmq
  (:require [zeromq.zmq :as zmq]
            [clojure.core.async :as a]))

(def zc (zmq/context))

(defn parse [zmq-str]
  (-> zmq-str
      (clojure.string/replace-first "SIPP-" "")
      (clojure.string/split #":")))

(defn consumer [results [msg-type timestamp scenario-name call-number call-id index actual-recvd :as msg]]
  (condp = msg-type
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


(defn run []
  (let [zs (->
            (zmq/socket zc :sub)
            (zmq/connect "tcp://127.0.0.1:5556")
            (zmq/subscribe "SIPP"))
        events-channel (a/chan)
        start-results {:successful-calls 0
                       :failed-calls 0
                       :scenario
                       (scenario-definition)}
        results  (atom start-results)]
    (poll-socket zs events-channel)
    (let [retchan (a/chan)]
      (a/go
       (loop [timeout-chan (a/timeout 1000)]
         (let [[val port] (a/alts!! [events-channel timeout-chan])]
           (if (nil? val)
             (do (a/>! retchan (assoc @results :timestamp (System/currentTimeMillis)))
                 (reset! results start-results)
                 (recur (a/timeout 1000)))
             (do (consumer results val)
                 (recur timeout-chan))))))
      retchan))
)
