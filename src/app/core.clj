(ns app.core
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [ring.adapter.jetty :as jetty]
            [integrant.core :as ig]
            [migratus.core :as migratus])
  (:gen-class))

(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(def config
  (-> "config.edn"
      io/resource
      aero/read-config))

(defmethod ig/init-key :default
  [_ arg-map]
  arg-map)

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
   (ring/router ["/api"
                 user-routes
                 admin-routes
                 cart-routes
                 discounts-routes
                 inventory-routes]
                {:data {:muuntaja m/instance
                        :middleware [muuntaja/format-middleware
                                     rrc/coerce-exceptions-middleware
                                     rrc/coerce-request-middleware
                                     rrc/coerce-response-middleware]}})))

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

(defn create-migration [name]
  (let [minimal-migration-config
        (-> config
            :app.services.database/migrations
            (select-keys [:migration-dir]))]
    (migratus/create minimal-migration-config name)))

(defn -main
  "starts server"
  [& args]
  (start-system))
