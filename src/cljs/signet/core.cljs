(ns signet.core
  (:require [strokes :refer [d3]]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [chord.client :refer [ws-ch]]
            [om.core :as om :include-macros true]
            [kioo.om :refer [content set-attr do-> substitute listen remove-attr add-class remove-class]]
            [kioo.core :refer [handle-wrapper]]
            [cljs.reader :refer [read-string] :as read])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [kioo.om :refer [defsnippet deftemplate]]))


(.log js/console "Kneel before kordano!")
