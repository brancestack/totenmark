(ns totenmark.http.routes
  (:require [clojure.set :as set]
            [totenmark.http.auth :as auth]
            [totenmark.http.handlers.auth :as handlers.auth]
            [totenmark.http.handlers.health :as handlers.health]
            [totenmark.http.handlers.products :as handlers.products]
            [totenmark.http.handlers.users :as handlers.users]
            [totenmark.http.rate-limit :as rate-limit]))

(defn- protected
  [handler]
  [auth/authenticate handler])

(def product-routes
  #{["/api/products" :get
     handlers.products/find-all
     :route-name :products/list]

    ["/api/products" :post
     (protected handlers.products/create!)
     :route-name :products/create]

    ["/api/products/:id" :get
     handlers.products/find-by-id
     :route-name :products/get]

    ["/api/products/:id" :patch
     (protected handlers.products/update!)
     :route-name :products/update]

    ["/api/products/:id" :delete
     (protected handlers.products/delete!)
     :route-name :products/delete]

    ["/api/products/:id/reservations" :post
     (protected handlers.products/reserve!)
     :route-name :reservations/create]

    ["/api/products/:id/reservations" :delete
     (protected handlers.products/release-reservation!)
     :route-name :reservations/delete]})

(defn user-routes
  [signup-limit]
  #{["/api/users" :post
     [signup-limit handlers.users/create!]
     :route-name :users/create]

    ["/api/users" :get
     (protected handlers.users/find-all)
     :route-name :users/list]

    ["/api/users/:id" :get
     (protected handlers.users/find-by-id)
     :route-name :users/get]

    ["/api/users/:id" :patch
     (protected handlers.users/update!)
     :route-name :users/update]

    ["/api/users/:id" :delete
     (protected handlers.users/delete!)
     :route-name :users/delete]})

(defn auth-and-health-routes
  [login-limit]
  #{["/api/auth/login" :post
     [login-limit handlers.auth/login!]
     :route-name :auth/login]

    ["/api/auth/logout" :post
     (protected handlers.auth/logout!)
     :route-name :auth/logout]

    ["/health" :get handlers.health/live :route-name :health/live]
    ["/ready" :get handlers.health/ready :route-name :health/ready]})

;; Mantidas temporariamente enquanto os primeiros clientes migram para /api/products
;; e /api/users. Não devem receber funcionalidades novas.
(defn legacy-routes
  [signup-limit]
  #{["/api/product/create" :post
     (protected handlers.products/create!)
     :route-name :legacy-product/create]
    ["/api/product/all" :get
     handlers.products/find-all
     :route-name :legacy-product/list]
    ["/api/product/update" :patch
     (protected handlers.products/update!)
     :route-name :legacy-product/update]
    ["/api/product/delete" :delete
     (protected handlers.products/delete!)
     :route-name :legacy-product/delete]
    ["/api/product/:id" :get
     handlers.products/find-by-id
     :route-name :legacy-product/get]

    ["/api/user/create" :post
     [signup-limit handlers.users/create!]
     :route-name :legacy-user/create]
    ["/api/user/all" :get
     (protected handlers.users/find-all)
     :route-name :legacy-user/list]
    ["/api/user/update" :patch
     (protected handlers.users/update!)
     :route-name :legacy-user/update]
    ["/api/user/delete" :delete
     (protected handlers.users/delete!)
     :route-name :legacy-user/delete]
    ["/api/user/:id" :get
     (protected handlers.users/find-by-id)
     :route-name :legacy-user/get]})

(defn routes
  []
  (let [signup-limit (rate-limit/signup)]
    (set/union product-routes
               (user-routes signup-limit)
               (auth-and-health-routes (rate-limit/login))
               (legacy-routes signup-limit))))
