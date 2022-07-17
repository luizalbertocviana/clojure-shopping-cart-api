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
  [_ {:keys [user-routes
             admin-routes
             cart-routes
             discounts-routes
             inventory-routes]}]
  (ring/ring-handler
   (ring/router
    ["/api"
     user-routes
     admin-routes
     cart-routes
     discounts-routes
     inventory-routes])))

(defmethod ig/init-key ::server
  [_ {:keys [handler port join?]}]
  (jetty/run-jetty handler {:port port :join? join?}))

(defmethod ig/halt-key! ::server
  [_ server]
  (.stop server))

(def system (atom nil))

(defn start-system []
  (ig/load-namespaces config)
  (reset! system (ig/init config)))

(defn stop-system []
  (ig/halt! @system)
  (reset! system nil))

(defn -main
  "starts server"
  [& args]
  (start-system))
