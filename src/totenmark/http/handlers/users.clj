(ns totenmark.http.handlers.users
  (:require [clojure.string :as str]
            [totenmark.db.users :as db.users]
            [totenmark.http.auth :as auth]
            [totenmark.http.response :as response]
            [totenmark.http.validation :as validation]
            [totenmark.logic.password :as password]))

(defn- parse-id
  [value]
  (let [parsed (cond
                 (integer? value) value
                 (string? value) (parse-long value)
                 :else nil)]
    (when (and parsed (pos? parsed))
      parsed)))

(defn- query-param
  [request name]
  (or (get-in request [:query-params name])
      (get-in request [:query-params (keyword name)])))

(defn- user-id
  [request]
  (parse-id (or (get-in request [:path-params :id])
                (get-in request [:json-params :id]))))

(defn- pagination
  [request]
  (let [limit-value (query-param request "limit")
        offset-value (query-param request "offset")
        limit (if (nil? limit-value) 20 (parse-long limit-value))
        offset (if (nil? offset-value) 0 (parse-long offset-value))]
    (when (and limit offset (<= 1 limit 100) (<= 0 offset))
      {:limit limit
       :offset offset
       :query-text (some-> (query-param request "q") str/trim not-empty)})))

(defn- normalize-user
  [attrs]
  (cond-> attrs
    (string? (:username attrs)) (update :username str/trim)
    (string? (:email attrs)) (update :email #(-> % str/trim str/lower-case))))

(defn create!
  [request]
  (let [body (normalize-user (:json-params request))
        errors (validation/user-create-errors body)]
    (if (seq errors)
      (response/json-response 422 {:msg "Revise os dados da conta." :errors errors})
      (try
        (case (:result (-> body
                           (update :password password/hash-password)
                           (db.users/create!)))
          :created (response/json-response 201 {:msg "Conta criada."})
          :email-taken (response/json-response 409 {:msg "Já existe uma conta com este e-mail."}))
        (catch Exception exception
          (response/failure "create-user" exception 500 "Não foi possível criar a conta."))))))

(defn find-all
  [request]
  (if-let [{:keys [limit offset] :as options} (pagination request)]
    (try
      (let [items (db.users/find-all options)
            total (db.users/count-all options)]
        (response/json-response 200
                                {:items items
                                 :pagination {:limit limit
                                              :offset offset
                                              :total total
                                              :has-more (< (+ offset (count items)) total)}}))
      (catch Exception exception
        (response/failure "list-users" exception 500 "Não foi possível listar os usuários.")))
    (response/json-response 422 {:msg "limit deve estar entre 1 e 100; offset não pode ser negativo."})))

(defn delete!
  [request]
  (let [id (user-id request)]
    (cond
      (nil? id)
      (response/json-response 422 {:msg "O id deve ser um número inteiro positivo."})

      (not (auth/owns-user? request id))
      (response/json-response 403 {:msg "Você só pode excluir a própria conta."})

      :else
      (try
        (db.users/delete! id)
        (response/json-response 200 {:msg "Conta excluída."})
        (catch Exception exception
          (response/failure "delete-user" exception 500 "Não foi possível excluir a conta."))))))

(defn update!
  [request]
  (let [body (normalize-user (:json-params request))
        id (user-id request)
        attrs (-> body (dissoc :id) (assoc :id id))
        errors (validation/user-update-errors attrs)]
    (cond
      (seq errors)
      (response/json-response 422 {:msg "Revise os dados da conta." :errors errors})

      (not (auth/owns-user? request id))
      (response/json-response 403 {:msg "Você só pode alterar a própria conta."})

      :else
      (try
        (let [password-changed? (contains? attrs :password)
              result (:result
                      (db.users/update! (cond-> attrs
                                          password-changed?
                                          (update :password password/hash-password))))]
          (case result
            :updated (response/json-response
                      200
                      {:msg (if password-changed?
                              "Senha atualizada. Entre novamente para continuar."
                              "Conta atualizada.")})
            :email-taken (response/json-response 409 {:msg "Já existe uma conta com este e-mail."})
            :not-found (response/json-response 404 {:msg "Usuário não encontrado."})))
        (catch Exception exception
          (response/failure "update-user" exception 500 "Não foi possível atualizar a conta."))))))

(defn find-by-id
  [request]
  (if-let [id (user-id request)]
    (try
      (if-let [user ((if (auth/owns-user? request id)
                       db.users/find-account-by-id
                       db.users/find-by-id)
                     id)]
        (response/json-response 200 user)
        (response/json-response 404 {:msg "Usuário não encontrado."}))
      (catch Exception exception
        (response/failure "find-user" exception 500 "Não foi possível buscar o usuário.")))
    (response/json-response 422 {:msg "O id deve ser um número inteiro positivo."})))
