(defproject app "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [aero "1.1.6"]
                 [metosin/reitit "0.5.18"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [integrant "0.8.0"]
                 [migratus "1.3.7"]
                 [org.slf4j/slf4j-log4j12 "1.7.36"]
                 [hikari-cp "2.14.0"]
                 [org.postgresql/postgresql "42.4.0"]]
  :main ^:skip-aot app.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
