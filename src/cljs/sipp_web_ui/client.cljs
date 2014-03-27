(ns hello-clojurescript
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [dommy.macros :refer [sel1]])
  (:require [jayq.core :refer [$]]
            [cljs.core.async :as a]
            [dommy.utils :as utils]
            [dommy.core :as dommy]
            [chord.client :refer [ws-ch]]))

(def options
  (clj->js { :xaxis { :mode "time" }} ))

(def updating-scenario (atom []))

(defn row-from-details [m]
  [:tr
   [:td (if (= "PAUSE" (:phase m))
          (str "[" (:details m) "ms]")
          (:details m) )]
   [:td (if (= "OUT" (:phase m))
          "--->"
          (if (= "IN" (:phase m))
            "<---"
            ""))]
   [:td (:msg m)]
   [:td (:timedout m)]
   [:td 0]])

(defn make-cumulative [periodic-data]
  (reduce
   (fn [curr [x y]]
     (let [[_ last-y] (last curr)
           cumulative (if last-y (+ last-y y) 0)]
       (conj curr [x cumulative])))
   []
   periodic-data))

(def successful (atom []))
(def failed (atom []))

(defn update-successful-and-failed [m]
  (swap! successful conj [(:timestamp m) (:successful-calls m)])
  (swap! failed conj [(:timestamp m) (:failed-calls m)]))

(defn graph [successful failed div-name]
  (let [data (clj->js [{:data successful
                        :label "Successful calls"
                        :color "green"
                        }
                       {:data failed
                        :label "Failed calls"
                        :color "red"
                        }
                       ])]
    (.plot js/jQuery ($ div-name) data options)) )

(defn cumulative-graph [successful failed div-name]
  (graph (make-cumulative successful) (make-cumulative failed) div-name))

(defn graph-both [successful failed div-name cumulative-div-name]
  (.log js/console successful)
  (graph successful failed div-name)
  (cumulative-graph successful failed cumulative-div-name))

(defn update-scenario [data]
  (if (empty? @updating-scenario)
   (reset! updating-scenario (:scenario data))
   (swap! updating-scenario
          #(mapv (fn [[a b]] (-> b
                                (assoc :msg (+ (get a :msg 0) (:msg b)))
                                (assoc :timedout (+ (get a :timedout 0) (:timedout b)))))
                 (partition 2 (interleave % (:scenario data))))))
 )

(go
 (.log js/console "Hello world")
 (let [ws (<! (ws-ch "ws://localhost:5000/ws"))]
   (loop [data (:message (<! ws))]
     (when data
       (do
         (update-successful-and-failed data)
          (graph-both @successful
                      @failed
                      "#flot-periodic"
                      "#flot")
          (update-scenario data)
          (dommy/clear! (sel1 :#data-body))
          (dommy/append! (sel1 :#data-body) (map row-from-details @updating-scenario))
          (recur (:message (<! ws)))))
     ))
)
