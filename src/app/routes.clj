(ns app.routes
  (:require [integrant.core :as ig]))

(defmethod ig/init-key ::admin
  [_ {:keys [admin-handler]}]
  ["/admin"
   [""
    {:post {:handler admin-handler}}]])

(defmethod ig/init-key ::cart
  [_ {:keys [active-session-middleware
             cart-entry-add-handler]}]
  ["/cart"
   {:middleware [active-session-middleware]}
   [""
    {:get {:handler (fn [_]
                      {:status 200
                       :body "hello from GET /cart"})}
     :delete {:handler (fn [_]
                         {:status 200
                          :body "hello from DELETE /cart"})}}]
   ["/add"
    {:put {:handler cart-entry-add-handler}}]
   ["/remove"
    {:put {:handler (fn [_]
                      {:status 200
                       :body "hello from /cart/remove"})}}]
   ["/totals"
    {:get {:handler (fn [_]
                      {:status 200
                       :body "hello from /cart/totals"})}}]])

(defmethod ig/init-key ::discounts
  [_ {:keys [active-session-middleware
             admin-session-middleware
             post-handler
             put-handler]}]
  ["/discounts"
   {:middleware [active-session-middleware]
    :post {:middleware [admin-session-middleware]
           :handler post-handler}
    :put {:handler put-handler}}])

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
             delete-handler
             amount-increase-handler
             amount-decrease-handler
             price-change-handler]}]
  ["/inventory"
   {:middleware [active-session-middleware
                 admin-session-middleware]}
   [""
    {:post {:handler post-handler}
     :delete {:handler delete-handler}}]
   ["/price"
    {:put {:handler price-change-handler}}]
   ["/increase"
    {:put {:handler amount-increase-handler}}]
   ["/decrease"
    {:put {:handler amount-decrease-handler}}]])
