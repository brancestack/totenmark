(defproject totenmark "0.2.0-SNAPSHOT"
  :description "Backend do Totenmark, um marketplace de venda e doação de usados"
  :url "https://github.com/brancestack/totenmark"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 [org.clojure/data.json "2.5.2"]
                 [io.pedestal/pedestal.http-kit "0.8.1"]
                 [com.github.seancorfield/next.jdbc "1.3.1118"]
                 [com.github.seancorfield/honeysql "2.7.1399"]
                 [org.xerial/sqlite-jdbc "3.53.2.0"]
                 [dev.weavejester/ragtime "0.12.1"]
                 [org.mindrot/jbcrypt "0.4"]
                 [buddy/buddy-sign "3.6.1-359"]
                 [org.slf4j/slf4j-simple "2.0.17"]]
  :main totenmark.core
  :repl-options {:init-ns totenmark.core})
