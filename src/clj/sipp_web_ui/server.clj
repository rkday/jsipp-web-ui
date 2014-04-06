(ns sipp-web-ui.server
  (:require [ring.adapter.jetty :as jetty]
            [sipp-web-ui.zmq :as zmq]
            [ring.middleware.resource :as resources]
            [ring.util.response :as response]
            [chord.http-kit :refer [with-channel]]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :as a])
  (:gen-class))

(defn mk-ws-handler [data-channel]
  (fn zmq-handler [req]
    (println "Ws handler")
    (with-channel req ws-ch
      (a/go
       (loop [data (a/<! data-channel)]
         (a/>! ws-ch data)
         (recur (a/<! data-channel)))))  ))

(defn handler [request]
  (response/redirect "/dashboard.html"))

(def app
  (-> handler
    (resources/wrap-resource "public")))

(defn -main [& args]
  (run-server (mk-ws-handler (zmq/run))  {:port 5000})
  (a/go (jetty/run-jetty app {:port 3000}))
  (println "Started WS server"))

