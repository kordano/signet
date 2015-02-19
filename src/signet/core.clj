(ns signet.core
  (:require [taoensso.timbre :as timbre]
            [konserve.store :refer [new-mem-store]]
            [geschichte.repo :as r]
            [geschichte.sync :refer [server-peer client-peer]]
            [geschichte.stage :as s]
            [geschichte.platform :refer [create-http-kit-handler! <!? start]]
            [clojure.core.async :refer [>!! <!!]]
            [aprint.core :refer [aprint]]
            [datomic.api :as d]))

(timbre/refer-timbre)


(comment

  (def repo (r/new-repository "kordano@topiq.es" "Bookmark collector"))


  )
