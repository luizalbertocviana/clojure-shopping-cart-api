(ns app.core
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [integrant.core :as ig])
  (:gen-class))

(def system
  (-> "system.edn"
      io/resource
      aero/read-config))

(defn handler [request]
  {:status 200
   :body (str "hello " (:greeting system))})

(def main-handler
  (ring/ring-handler
   (ring/router
    [["/api"
      ["/hello"
       {:get {:handler handler}}]]])))

(def server (atom nil))

(defn start-server []
  (reset! server (jetty/run-jetty main-handler {:port 3000 :join? false})))

(defn stop-server []
  (.stop @server)
  (reset! server nil))

(defn -main
  "starts server"
  [& args]
  (start-server))
