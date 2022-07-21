(ns app.middleware
  (:require [app.utils :as utils]
            [integrant.core :as ig])
  (:import [java.util UUID]))

(defmethod ig/init-key ::admin-session
  [_ {:keys [querier]}]
  (fn [handler]
    (fn [req]
      (let [body-params (:body-params req)
            session-id (UUID/fromString (:session body-params))]
        (if (utils/admin-session querier session-id)
          (handler req)
          {:status 403
           :body (str "Session " session-id " does not belong to an admin user")})))))

(defmethod ig/init-key ::active-session
  [_ {:keys [querier]}]
  (fn [handler]
    (fn [req]
      (let [body-params (:body-params req)
            session-id (UUID/fromString (:session body-params))]
        (if (utils/session-is-active querier session-id)
          (handler req)
          {:status 401
           :body (str "Session " session-id " is not active")})))))
