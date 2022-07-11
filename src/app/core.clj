(ns app.core
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [integrant.core :as ig])
  (:gen-class))

(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(def config
  (-> "config.edn"
      io/resource
      aero/read-config))

(defmethod ig/init-key ::basic-handler
  [_ {:keys [greeting]}]
  (fn [request]
    {:status 200
     :body (str "hello " greeting)}))

(defmethod ig/init-key ::main-handler
  [_ {:keys [handler]}]
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
