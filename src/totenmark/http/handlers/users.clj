(ns totenmark.http.handlers.users
  (:require [totenmark.db.users :as db.users]
            [totenmark.http.auth :as auth]
            [totenmark.http.response :as response]
            [totenmark.http.validation :as validation]
            [totenmark.logic.password :as password]))

(defn- parse-id
  [value]
  (cond
    (integer? value) value
    (string? value) (parse-long value)
    :else nil))

(defn- query-param
  [request name]
  (or (get-in request [:query-params name])
      (get-in request [:query-params (keyword name)])))

(defn- pagination
  [request]
  (let [limit-value (query-param request "limit")
        offset-value (query-param request "offset")
        limit (if (nil? limit-value) 50 (parse-long limit-value))
        offset (if (nil? offset-value) 0 (parse-long offset-value))]
    (when (and limit offset (<= 1 limit 100) (<= 0 offset))
      {:limit limit
       :offset offset
       :query-text (query-param request "q")})))

(defn create!
  [request]
  (let [body (:json-params request)
        errors (validation/user-create-errors body)]
    (if (seq errors)
      (response/json-response 422 {:msg "Invalid user data." :errors errors})
      (try
        (-> body
            (update :password password/hash-password)
            (db.users/create!))
        (response/json-response 201 {:msg "User created successfully."})
        (catch Exception exception
          (response/failure "create user" exception 400 "Failed to create user."))))))

(defn find-all
  [request]
  (if-let [options (pagination request)]
    (try
      (response/json-response 200 (db.users/find-all options))
      (catch Exception exception
        (response/failure "list users" exception 500 "Failed to retrieve users.")))
    (response/json-response 422 {:msg "limit must be between 1 and 100; offset must be non-negative."})))

(defn delete!
  [request]
  (let [id (parse-id (get-in request [:json-params :id]))]
    (cond
      (nil? id)
      (response/json-response 422 {:msg "id must be an integer."})

      (not (auth/owns-user? request id))
      (response/json-response 403 {:msg "You can only delete your own account."})

      :else
      (try
        (db.users/delete! id)
        (response/json-response 200 {:msg "User deleted successfully."})
        (catch Exception exception
          (response/failure "delete user" exception 400 "Failed to delete user."))))))

(defn update!
  [request]
  (let [body (:json-params request)
        id (parse-id (:id body))
        attrs (assoc body :id id)
        errors (validation/user-update-errors attrs)]
    (cond
      (seq errors)
      (response/json-response 422 {:msg "Invalid user data." :errors errors})

      (not (auth/owns-user? request id))
      (response/json-response 403 {:msg "You can only update your own account."})

      :else
      (try
        (db.users/update! (cond-> attrs
                            (contains? attrs :password)
                            (update :password password/hash-password)))
        (response/json-response 200 {:msg "User updated successfully."})
        (catch Exception exception
          (response/failure "update user" exception 400 "Failed to update user."))))))

(defn find-by-id
  [request]
  (if-let [id (parse-id (get-in request [:path-params :id]))]
    (try
      (if-let [user (db.users/find-by-id id)]
        (response/json-response 200 user)
        (response/json-response 404 {:msg "User not found."}))
      (catch Exception exception
        (response/failure "find user by id" exception 500 "Failed to retrieve user.")))
    (response/json-response 422 {:msg "id must be an integer."})))
