(ns signet.core
  (:require [taoensso.timbre :as timbre]
            [konserve.store :refer [new-mem-store]]
            [konserve.protocols :refer [-get-in -assoc-in]]
            [geschichte.repo :as r]
            [geschichte.sync :refer [server-peer client-peer]]
            [geschichte.stage :as s]
            [geschichte.platform :refer [create-http-kit-handler! <!? start]]
            [clojure.core.async :refer [>!! <!!]]
            [aprint.core :refer [aprint]]
            [datomic.api :as d]))

(timbre/refer-timbre)


(comment

  (def store (<!? (new-mem-store)))

  (def peer (client-peer "CLIENT" store identity))

  (def stage (<!! (s/create-stage! "kordano@topiq.es" peer eval)))

  (def r-id (<!? (s/create-repo! stage "Bookmark collection")))

  (aprint r-id)

  (<!? (s/transact stage ["kordano@topiq.es" r-id "master"]
                   '(fn [old params] (merge old params))
                   {:bookmark "q.es"}))


  (<!? (s/commit! stage {"kordano@topiq.es" {r-id #{"master"}}}))


  (<!? (-get-in store ["kordano@topiq.es"]))


  (aprint (get-in @stage ["kordano@topiq.es" r-id]))

  (aprint peer)

  )
