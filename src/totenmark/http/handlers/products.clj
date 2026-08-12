(ns totenmark.http.handlers.products
  (:require [clojure.string :as str]
            [totenmark.db.products :as db.products]
            [totenmark.db.reservations :as db.reservations]
            [totenmark.http.response :as response]
            [totenmark.http.validation :as validation]))

(defn- parse-id
  [value]
  (let [parsed (cond
                 (integer? value) value
                 (string? value) (parse-long value)
                 :else nil)]
    (when (and parsed (pos? parsed))
      parsed)))

(defn- product-id
  [request]
  (parse-id (or (get-in request [:path-params :id])
                (get-in request [:json-params :product-id]))))

(defn- query-param
  [request name]
  (or (get-in request [:query-params name])
      (get-in request [:query-params (keyword name)])))

(defn- list-options
  [request]
  (let [limit-value (query-param request "limit")
        offset-value (query-param request "offset")
        user-id-value (query-param request "user-id")
        limit (if (nil? limit-value) 20 (parse-long limit-value))
        offset (if (nil? offset-value) 0 (parse-long offset-value))
        user-id (when user-id-value (parse-id user-id-value))
        category (query-param request "category")
        status (query-param request "status")]
    (when (and limit offset
               (<= 1 limit 100)
               (<= 0 offset)
               (or (nil? user-id-value) (some? user-id))
               (or (nil? category) (#{"donation" "sale"} category))
               (or (nil? status) (#{"available" "reserved"} status)))
      {:limit limit
       :offset offset
       :query-text (some-> (query-param request "q") str/trim not-empty)
       :user-id user-id
       :category category
       :status status})))

(defn- owner-id
  [product]
  (:user-id product))

(defn- may-change?
  [request product]
  (= (get-in request [:identity :user-id]) (owner-id product)))

(defn- normalize-product
  [attrs]
  (cond-> attrs
    (string? (:product-name attrs)) (update :product-name str/trim)
    (string? (:description attrs)) (update :description str/trim)))

(defn create!
  [request]
  (let [body (-> (:json-params request)
                 normalize-product
                 (update :status #(or % "available")))
        errors (validation/product-create-errors body)]
    (if (seq errors)
      (response/json-response 422 {:msg "Revise os dados do anúncio." :errors errors})
      (try
        (let [product (db.products/create!
                       (assoc body :user-id (get-in request [:identity :user-id])))]
          (response/json-response 201 {:msg "Anúncio publicado."
                                       :product product}))
        (catch Exception exception
          (response/failure "create-product" exception 500 "Não foi possível publicar o anúncio."))))))

(defn find-all
  [request]
  (if-let [{:keys [limit offset] :as options} (list-options request)]
    (try
      (let [items (db.products/find-all options)
            total (db.products/count-all options)]
        (response/json-response
         200
         {:items items
          :pagination {:limit limit
                       :offset offset
                       :total total
                       :has-more (< (+ offset (count items)) total)}}))
      (catch Exception exception
        (response/failure "list-products" exception 500 "Não foi possível carregar os anúncios.")))
    (response/json-response
     422
     {:msg "Filtros inválidos. Confira limit, offset, user-id, category e status."})))

(defn delete!
  [request]
  (if-let [id (product-id request)]
    (try
      (if-let [product (db.products/find-by-id id)]
        (if (may-change? request product)
          (do
            (db.products/delete! id)
            (response/json-response 200 {:msg "Anúncio excluído."}))
          (response/json-response 403 {:msg "Você só pode excluir os próprios anúncios."}))
        (response/json-response 404 {:msg "Anúncio não encontrado."}))
      (catch Exception exception
        (response/failure "delete-product" exception 500 "Não foi possível excluir o anúncio.")))
    (response/json-response 422 {:msg "O id do anúncio deve ser um número inteiro positivo."})))

(defn update!
  [request]
  (let [id (product-id request)
        attrs (-> (:json-params request)
                  normalize-product
                  (dissoc :product-id)
                  (assoc :product-id id))
        errors (validation/product-update-errors attrs)]
    (if (seq errors)
      (response/json-response 422 {:msg "Revise os dados do anúncio." :errors errors})
      (try
        (if-let [product (db.products/find-by-id id)]
          (if (may-change? request product)
            (response/json-response
             200
             {:msg "Anúncio atualizado."
              :product (db.products/update! attrs)})
            (response/json-response 403 {:msg "Você só pode alterar os próprios anúncios."}))
          (response/json-response 404 {:msg "Anúncio não encontrado."}))
        (catch Exception exception
          (response/failure "update-product" exception 500 "Não foi possível atualizar o anúncio."))))))

(defn find-by-id
  [request]
  (if-let [id (product-id request)]
    (try
      (if-let [product (db.products/find-by-id id)]
        (response/json-response 200 product)
        (response/json-response 404 {:msg "Anúncio não encontrado."}))
      (catch Exception exception
        (response/failure "find-product" exception 500 "Não foi possível buscar o anúncio.")))
    (response/json-response 422 {:msg "O id do anúncio deve ser um número inteiro positivo."})))

(defn reserve!
  [request]
  (if-let [id (product-id request)]
    (try
      (let [{:keys [result reservation]}
            (db.reservations/reserve! id (get-in request [:identity :user-id]))]
        (case result
          :reserved (response/json-response 201 {:msg "Reserva confirmada."
                                                 :reservation reservation})
          :not-found (response/json-response 404 {:msg "Anúncio não encontrado."})
          :owner-cannot-reserve (response/json-response 409 {:msg "Você não pode reservar o próprio anúncio."})
          :unavailable (response/json-response 409 {:msg "Este produto não está mais disponível."})))
      (catch Exception exception
        (response/failure "reserve-product" exception 500 "Não foi possível concluir a reserva.")))
    (response/json-response 422 {:msg "O id do anúncio deve ser um número inteiro positivo."})))

(defn release-reservation!
  [request]
  (if-let [id (product-id request)]
    (try
      (case (:result (db.reservations/release!
                      id (get-in request [:identity :user-id])))
        :released (response/json-response 200 {:msg "Reserva cancelada."})
        :not-found (response/json-response 404 {:msg "Anúncio não encontrado."})
        :not-reserved (response/json-response 409 {:msg "Este produto não está reservado."})
        :forbidden (response/json-response 403 {:msg "Somente o comprador ou o vendedor pode cancelar a reserva."}))
      (catch Exception exception
        (response/failure "release-reservation" exception 500 "Não foi possível cancelar a reserva.")))
    (response/json-response 422 {:msg "O id do anúncio deve ser um número inteiro positivo."})))
