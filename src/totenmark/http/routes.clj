(ns totenmark.http.routes
  (:require [clojure.set :as set]
            [totenmark.http.handlers.users :as handlers.users]
            [totenmark.http.handlers.products :as handlers.products]
            [totenmark.http.handlers.auth :as handlers.auth]
            [totenmark.http.auth :as auth]))

(def product-routes
  #{["/api/product/create" :post   auth/authenticate handlers.products/create!    :route-name :product/create]
    ["/api/product/all"    :get    auth/authenticate handlers.products/find-all   :route-name :product/find-all]
    ["/api/product/update" :patch  auth/authenticate handlers.products/update!    :route-name :product/update]
    ["/api/product/delete" :delete auth/authenticate handlers.products/delete!    :route-name :product/delete]

    ["/api/product/:id"    :get    auth/authenticate handlers.products/find-by-id :route-name :product/get-by-id]})

(def user-routes
  #{["/api/user/create" :post   handlers.users/create!    :route-name :user/create]
    ["/api/user/all"    :get    auth/authenticate handlers.users/find-all   :route-name :user/find-all]
    ["/api/user/update" :patch  auth/authenticate handlers.users/update!    :route-name :user/update]
    ["/api/user/delete" :delete auth/authenticate handlers.users/delete!    :route-name :user/delete]

    ["/api/user/:id"    :get    auth/authenticate handlers.users/find-by-id :route-name :user/get-by-id]})

(def security
  #{["/api/auth/login" :post handlers.auth/login! :route-name :auth/login]})

(def routes
  (set/union product-routes user-routes security))
