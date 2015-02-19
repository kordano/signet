(defproject signet "0.1.0-SNAPSHOT"
  :description "FIXME: write description"

  :url "http://example.com/FIXME"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.datomic/datomic-free "0.9.5130"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.taoensso/timbre "3.3.1"]
                 [net.polyc0l0r/konserve "0.2.3"]
                 [aprint "0.1.3"]
                 [net.polyc0l0r/geschichte "0.1.0-SNAPSHOT"]]
  :main signet.core
  )
