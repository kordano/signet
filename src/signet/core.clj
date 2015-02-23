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
            [clojure.core.async :refer [>!! <!!]]
            [aprint.core :refer [aprint]]
            [datomic.api :as d]))

(timbre/refer-timbre)

(comment

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

  (<!? (s/branch! stage
                  ["kordano@topiq.es" r-id]
                  "Some bookmarks"
                  (first (get-in @stage ["kordano@topiq.es" r-id :state :branches "master"]))))

  (<!? (-get-in store ["kordano@topiq.es" r-id :branches "Some bookmarks"]))

  (get-in @stage ["kordano@topiq.es" r-id])

  (defn load-key [id]
    (<!? (-get-in store [id])))

  (def conn (<!? (s/branch-value store mapped-eval
                                 (get-in @stage ["kordano@topiq.es" r-id])
                                 "Some bookmarks")))

  (<!? (-assoc-in store ["schema"] (read-string (slurp "resources/schema.edn"))))





  )
