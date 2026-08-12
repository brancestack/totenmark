(ns totenmark.http.validation
  (:require [clojure.string :as str]
            [totenmark.logic.password :as password]))

(defn- non-blank-string?
  [value]
  (and (string? value) (not (str/blank? value))))

(defn- valid-email?
  [value]
  (and (non-blank-string? value)
       (boolean (re-matches #"^[^\s@]+@[^\s@]+\.[^\s@]+$" value))))

(defn- positive-integer?
  [value]
  (and (integer? value) (pos? value)))

(defn- valid-price?
  [value]
  (and (integer? value)
       (<= 0 value Long/MAX_VALUE)))

(defn- valid-description?
  [value]
  (or (nil? value) (string? value)))

(defn- valid-password?
  [value]
  (and (string? value)
       (<= 8 (count value))
       (password/supported-length? value)))

(defn- valid-http-url?
  [value]
  (try
    (let [uri (java.net.URI. value)]
      (and (non-blank-string? value)
           (#{"http" "https"} (some-> (.getScheme uri) str/lower-case))
           (non-blank-string? (.getHost uri))))
    (catch Exception _ false)))

(defn- valid-images?
  [image-urls]
  (and (vector? image-urls)
       (<= (count image-urls) 8)
       (every? valid-http-url? image-urls)))

(defn user-create-errors
  [{:keys [username email password] :as attrs}]
  (cond-> []
    (not (non-blank-string? username)) (conj "username é obrigatório")
    (not (valid-email? email)) (conj "informe um e-mail válido")
    (not (valid-password? password))
    (conj "a senha deve ter pelo menos 8 caracteres e no máximo 72 bytes")
    (seq (remove #{:username :email :password} (keys attrs)))
    (conj "a requisição contém campos não suportados")))

(defn user-update-errors
  [attrs]
  (let [allowed #{:id :username :email :password}
        changed (dissoc attrs :id)]
    (cond-> []
      (not (positive-integer? (:id attrs))) (conj "id deve ser um número inteiro positivo")
      (empty? changed) (conj "informe ao menos um campo para alterar")
      (seq (remove allowed (keys attrs))) (conj "a requisição contém campos não suportados")
      (and (contains? attrs :username) (not (non-blank-string? (:username attrs))))
      (conj "username não pode ficar em branco")
      (and (contains? attrs :email) (not (valid-email? (:email attrs))))
      (conj "informe um e-mail válido")
      (and (contains? attrs :password)
           (not (valid-password? (:password attrs))))
      (conj "a senha deve ter pelo menos 8 caracteres e no máximo 72 bytes"))))

(defn product-create-errors
  [{:keys [product-name price category status image-urls] :as attrs}]
  (cond-> []
    (not (non-blank-string? product-name)) (conj "product-name é obrigatório")
    (not (valid-description? (:description attrs))) (conj "description deve ser texto ou null")
    (not (valid-price? price)) (conj "price deve ser um inteiro entre zero e o limite do SQLite")
    (not (#{"donation" "sale"} category)) (conj "category deve ser donation ou sale")
    (not= "available" status) (conj "um anúncio novo deve começar como available")
    (and (some? image-urls) (not (valid-images? image-urls)))
    (conj "image-urls aceita no máximo 8 URLs HTTP(S) válidas")
    (seq (remove #{:product-name :description :price :category :status :image-urls} (keys attrs)))
    (conj "a requisição contém campos não suportados")))

(defn product-update-errors
  [attrs]
  (let [allowed #{:product-id :product-name :description :price :category :image-urls}
        changed (dissoc attrs :product-id)]
    (cond-> []
      (not (positive-integer? (:product-id attrs))) (conj "product-id deve ser um número inteiro positivo")
      (empty? changed) (conj "informe ao menos um campo para alterar")
      (seq (remove allowed (keys attrs))) (conj "a requisição contém campos não suportados")
      (and (contains? attrs :product-name)
           (not (non-blank-string? (:product-name attrs))))
      (conj "product-name não pode ficar em branco")
      (and (contains? attrs :description)
           (not (valid-description? (:description attrs))))
      (conj "description deve ser texto ou null")
      (and (contains? attrs :price)
           (not (valid-price? (:price attrs))))
      (conj "price deve ser um inteiro entre zero e o limite do SQLite")
      (and (contains? attrs :category)
           (not (#{"donation" "sale"} (:category attrs))))
      (conj "category deve ser donation ou sale")
      (and (contains? attrs :image-urls)
           (not (valid-images? (:image-urls attrs))))
      (conj "image-urls aceita no máximo 8 URLs HTTP(S) válidas"))))
