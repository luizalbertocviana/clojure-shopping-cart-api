(defproject app "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [aero "1.1.6"]
                 [metosin/reitit "0.5.18"]
                 [metosin/reitit-middleware "0.5.18"]
                 [metosin/muuntaja "0.6.8"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [integrant "0.8.0"]
                 [migratus "1.3.7"]
                 [com.fzakaria/slf4j-timbre "0.3.21"]
                 [com.taoensso/timbre "5.2.1"]
                 [hikari-cp "2.14.0"]
                 [org.postgresql/postgresql "42.4.0"]
                 [com.github.seancorfield/honeysql "2.2.891"]]
  :main ^:skip-aot app.core
  :target-path "target/%s"
  :profiles {:test {:dependencies [[clj-http "3.12.3"]
                                   [org.clojure/data.json "2.4.0"]]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
