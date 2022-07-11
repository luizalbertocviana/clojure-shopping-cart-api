(ns app.core
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty])
  (:gen-class))

(def system
  (-> "system.edn"
      io/resource
      aero/read-config))

(defn -main
  "Now I am greeting from a system config read by aero"
  [& args]
  (println (str "hello " (:greeting system))))
