(defproject sipp-web-ui "0.2.0"
  :description "A demo web UI for the jSIPp test tool"
  :url "http://github.com/rkday/jsipp"
  :license {:name "MIT license"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2156"]
                 [ring "1.2.1"]
                 [jayq "2.5.0"]
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [org.clojure/clojure "1.5.1"]
                 [org.zeromq/jeromq "0.3.1"]
                 [org.zeromq/cljzmq "0.1.1" :exclusions [org.zeromq/jzmq]]
                 [prismatic/dommy "0.1.2"]
                 [jarohen/chord "0.3.1"]]
  :plugins [[lein-cljsbuild "1.0.2"]
            [lein-ring "0.8.10"]]
  :hooks [leiningen.cljsbuild]
  :source-paths ["src/clj"]
  :cljsbuild {
    :builds {
      :main {
        :source-paths ["src/cljs"]
        :compiler {:output-to "resources/public/js/cljs.js"
                   :optimizations :simple
                   :pretty-print true}
        :jar true}}}
  :main sipp-web-ui.server
  :ring {:handler sipp-web-ui.server/app})

