(defproject signet "0.1.0-SNAPSHOT"

  :description "Commit graph visualisation"

  :url "http://example.com/FIXME"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2411"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.taoensso/timbre "3.3.1"]
                 [aprint "0.1.3"]

                 ;; backend
                 [net.polyc0l0r/konserve "0.2.3"]
                 [net.polyc0l0r/geschichte "0.1.0-SNAPSHOT"]
                 [com.datomic/datomic-free "0.9.5130"]
                 [http-kit "2.1.19"]
                 [ring "1.3.1"]
                 [compojure "1.2.1"]

                 ;frontend
                 [jarohen/chord "0.4.2"]
                 [com.facebook/react "0.12.1"]
                 [om "0.7.3"]
                 [kioo "0.4.0"]
                 [net.drib/strokes "0.5.1"]]

  :main signet.core

  :source-paths ["src/cljs" "src/clj"]

  :plugins [[lein-cljsbuild "1.0.3"]]

  :cljsbuild
  {:builds
   [{:source-paths ["src/cljs"]
     :compiler
     {:output-to "resources/public/js/compiled/main.js"
      :output-dir "resources/public/js/compiled/out"
      :optimizations :none
      :pretty-print false
      :source-map "main.js.map"}}]}
  )
