(ns signet.core
  (:require [taoensso.timbre :as timbre]
            [konserve.store :refer [new-mem-store]]
            [konserve.protocols :refer [-get-in -assoc-in]]
            [geschichte.repo :as r]
            [geschichte.sync :refer [server-peer client-peer]]
            [geschichte.stage :as s]
            [geschichte.platform :refer [create-http-kit-handler! <!? start]]
            [geschichte.p2p.publish-on-request :refer [publish-on-request]]
            [geschichte.p2p.hash :refer [ensure-hash]]
            [geschichte.p2p.fetch :refer [fetch]]
            [geschichte.p2p.block-detector :refer [block-detector]]
            [signet.db :refer :all]
            [org.httpkit.server :refer [with-channel on-receive on-close run-server send!]]
            [clojure.java.io :as io]
            [compojure.route :refer [resources]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.handler :refer [site api]]
            [clojure.core.async :refer [>!! <!!]]
            [aprint.core :refer [aprint]]
            [datomic.api :as d]))

(timbre/refer-timbre)

(defn dispatch-request
  "Dispatch incoming requests"
  [{:keys [topic data]}]
  (case topic
    :graph {:a 1 :b 2}
    :unrelated))


(defn ws-handler
  "Handle incoming websocket requests"
  [request]
  (with-channel request channel
    (on-close channel (fn [msg] (info " - CONN - " channel " closed!")))
    (on-receive channel (fn [msg]
                          (send! channel (pr-str (dispatch-request (read-string msg))))))))


(defroutes handler
  (resources "/")
  (GET "/data/ws" [] ws-handler)
  (GET "/*" [] (io/resource "public/index.html")))


(defn -main [& args]
  (let [port (-> args first read-string)]
    (info "SERVER - Warming up...")
    (run-server (site #'handler) {:port (or port 8082) :join? false})
    (info "SERVER - running!")
    (info  (str "Visit http://localhost:" (or port 8082)))))




(comment

  (def stop-server (run-server (site #'handler) {:port 8091 :join? false}))

  (stop-server)

  (def store (<!? (new-mem-store)))

  (def peer (server-peer (create-http-kit-handler! "ws://127.0.0.1:31744") ;; TODO client-peer?
                         store
                         (comp (partial block-detector :peer-core)
                               (partial fetch store)
                               ensure-hash
                               (partial publish-on-request store)
                               (partial block-detector :p2p-surface))))

  (start peer)

  (def stage (<!! (s/create-stage! "kordano@topiq.es" peer eval)))

  (<!? (s/connect! stage "ws://127.0.0.1:31744"))

  (def r-id (<!? (s/create-repo! stage "Bookmark collection")))

  (<!? (s/transact stage ["kordano@topiq.es" r-id "master"]
                   [[(find-fn 'create-db)
                     {:name "bookmkarks-2"}]
                    [(find-fn 'transact-schema)
                     (-> "resources/schema.edn"
                         slurp
                         read-string)]]))

  (<!? (s/commit! stage {"kordano@topiq.es" {r-id #{"master"}}}))

  (<!? (s/transact stage ["kordano@topiq.es" r-id "master"]
                   [[(find-fn 'bookmarks->datoms)
                     {:title "turing"
                      :url "turing.com"
                      :user "kordano@topiq.es"}]]))

  (<!? (s/commit! stage {"kordano@topiq.es" {r-id #{"master"}}}))

  (<!? (s/branch! stage
                  ["kordano@topiq.es" r-id]
                  "bookmarks"
                  (first (get-in @stage ["kordano@topiq.es" r-id :state :branches "master"]))))


  (<!? (s/checkout! stage ["kordano@topiq.es" r-id] "bookmarks"))


  (<!? (s/transact stage ["kordano@topiq.es" r-id "bookmarks"]
                   [[(find-fn 'bookmarks->datoms)
                     {:title "test"
                      :url "test.com"
                      :user "kordano@topiq.es"}]]))

  (<!? (s/commit! stage {"kordano@topiq.es" {r-id #{"bookmarks"}}}))

  (<!? (s/checkout! stage ["kordano@topiq.es" r-id] "master"))

  (<!? (s/transact stage ["kordano@topiq.es" r-id "master"]
                   [[(find-fn 'bookmarks->datoms)
                     {:title "turing"
                      :url "turing.com"
                      :user "kordano@topiq.es"}]]))

  (<!? (s/commit! stage {"kordano@topiq.es" {r-id #{"master"}}}))


  (<!? (s/merge! stage ["kordano@topiq.es" r-id "bookmarks"]
                 (get-in @stage ["kordano@topiq.es" r-id :state :branches "bookmarks"])))


  (<!? (s/commit! stage {"kordano@topiq.es" {r-id #{"master"}}}))



  (aprint (get-in @stage ["kordano@topiq.es" r-id :state]))


  ;; this needs a moment, datomic has to be initialized
  (def conn (<!? (s/branch-value store mapped-eval
                                 (get-in @stage ["kordano@topiq.es" r-id])
                                 "Some bookmarks")))


  (d/q '[:find ?e ?u ?t
         :where
         [?e :bookmark/title ?t]
         [?e :bookmark/user ?u]]
       (d/db conn))





  )
