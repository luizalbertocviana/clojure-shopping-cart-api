(ns app.utils
  (:require [honey.sql :as sql]))

(defn session-id->user-id [querier session-id]
  (let [query {:select [:user-id]
               :from [:sessions]
               :where [:= :id session-id]}
        result (querier (sql/format query))]
    (-> result
        (nth 0)
        :user_id)))

(defn user-exists [querier name]
  (let [query {:select [:id]
               :from [:users]
               :where [:= :name name]}
        query-result (querier (sql/format query))
        name-occurrences (count query-result)]
    (if (= 1 name-occurrences)
      (-> query-result
          (nth 0)
          :id)
      nil)))

(defn session-is-active [querier session-id]
  (let [session-is-active-query {:select [[[:count :*]]]
                                 :from [:sessions]
                                 :where [:and [:= :id session-id]
                                              [:<= [:now] :expires-on]]}
        session-is-active-result (querier (sql/format session-is-active-query))]
    (-> session-is-active-result
        (nth 0)
        :count
        (= 1))))

(defn admin-session [querier session-id]
  (let [admin-session-query {:select [[[:count :*]]]
                             :from [:sessions :admins]
                             :where [:and [:= :sessions.user-id :admins.user-id]
                                          [:= :sessions.id session-id]]}
        admin-session-result (querier (sql/format admin-session-query))]
    (-> admin-session-result
        (nth 0)
        :count
        (= 1))))
