(ns app.routes
  (:require [integrant.core :as ig]))

(defmethod ig/init-key ::admin
  [_ {:keys [admin-handler]}]
  ["/admin"
   [""
    {:post {:handler admin-handler}}]])

(defmethod ig/init-key ::cart
  [_ _]
  ["/cart"
   [""
    {:get {:handler (fn [_]
                      {:status 200
                       :body "hello from GET /cart"})}
     :delete {:handler (fn [_]
                         {:status 200
                          :body "hello from DELETE /cart"})}}]
   ["/add"
    {:put {:handler (fn [_]
                      {:status 200
                       :body "hello from /cart/add"})}}]
   ["/remove"
    {:put {:handler (fn [_]
                      {:status 200
                       :body "hello from /cart/remove"})}}]
   ["/totals"
    {:get {:handler (fn [_]
                      {:status 200
                       :body "hello from /cart/totals"})}}]])

(defmethod ig/init-key ::discounts
  [_ _]
  ["/discounts"
   {:post {:handler (fn [_]
                      {:status 200
                       :body "hello from POST /discounts"})}
    :put {:handler (fn [_]
                     {:status 200
                      :body "hello from PUT /discounts"})}}])

(defmethod ig/init-key ::user
  [_ {:keys [create-handler
             login-handler
             logout-handler]}]
  ["/user"
   ["/create"
    {:post {:handler create-handler}}]
   ["/login"
    {:post {:handler login-handler}}]
   ["/logout"
    {:post {:handler logout-handler}}]])

(defmethod ig/init-key ::inventory
  [_ {:keys [active-session-middleware
             admin-session-middleware
             post-handler
             delete-handler]}]
  ["/inventory"
   {:middleware [active-session-middleware
                 admin-session-middleware]}
   [""
    {:post {:handler post-handler}
     :delete {:handler delete-handler}}]
   ["/price"
    {:put {:handler (fn [_]
                      {:status 200
                       :body "hello from /inventory/price"})}}]
   ["/increase"
    {:put {:handler (fn [_]
                      {:status 200
                       :body "hello from /inventory/increase"})}}]
   ["/decrease"
    {:put {:handler (fn [_]
                      {:status 200
                       :body "hello from /inventory/decrease"})}}]])
