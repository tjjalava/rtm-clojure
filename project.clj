(defproject rtm-clojure "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [ring/ring-devel "1.6.3" :scope "provided"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.3.1"]
                 [clj-http "3.8.0"]
                 [cheshire "5.8.0"]
                 [compojure "1.6.0"]
                 [yogthos/config "0.8"]]
  :ring {:handler rtm.core/app}
  :plugins [[lein-ring "0.12.1"]]
  :main rtm.core
  :profiles {:uberjar {:aot :all}})
