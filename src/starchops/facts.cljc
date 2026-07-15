(ns starchops.facts
  "Reference facts for starches-and-starch-products manufacturing: product-type
  extraction/refining parameters (moisture/purity/sulfite-residue/microbial-load/
  granulation windows), jurisdiction food-safety-declaration and
  evidence-checklist requirements, and per-raw-material allergen data. This
  namespace contains pure lookup functions for regulatory/food-safety
  compliance checks -- the Governor calls these to independently validate
  proposals; the advisor's confidence is never sufficient on its own."
  (:require [clojure.set :as set]))

(def product-types
  "Valid starch-product categories and their safe extraction/refining windows.
  `purity-percent` is the finished starch's dry-basis purity (100 minus
  residual protein/fiber/fat left over from incomplete separation) -- a core
  extraction-quality indicator, analogous to a grain mill's ash-content but
  measuring how cleanly the starch granule was separated from the rest of
  the raw material rather than how much bran/germ remains in a flour.
  `granulation-microns` is the target particle-size window for the dried,
  classified finished powder. `sulfite-residue-max-ppm` is the maximum
  allowable residual sulfur dioxide (as SO2) left from the steeping/
  bleaching step -- deliberately per-product-type since corn wet-milling
  routinely steeps in a dilute sulfurous-acid solution to loosen the
  starch-gluten matrix (leaving a real, regulated SO2 residue), while
  potato and cassava starch extraction is a purely mechanical/aqueous
  process with a much lower expected residue. `microbial-load-max-cfu` is
  the maximum allowable microbial colony count (CFU/g) -- starch slurries
  sit at high moisture and near-neutral pH for extended dwell times during
  extraction/refining, which is a real spoilage/pathogen-growth risk
  distinct from the dry-storage risk captured by `moisture-target-percent`."
  {:starch/corn-native
   {:id :starch/corn-native
    :name "コーンスターチ（ネイティブ）"
    :moisture-target-percent 12.0
    :moisture-tolerance-percent 0.5
    :purity-min-percent 99.0
    :purity-max-percent 100.0
    :granulation-min-microns 5
    :granulation-max-microns 25
    :sulfite-residue-max-ppm 50
    :microbial-load-max-cfu 1000}

   :starch/potato-native
   {:id :starch/potato-native
    :name "ばれいしょでん粉（ネイティブ）"
    :moisture-target-percent 18.0
    :moisture-tolerance-percent 1.0
    :purity-min-percent 98.5
    :purity-max-percent 100.0
    :granulation-min-microns 15
    :granulation-max-microns 100
    :sulfite-residue-max-ppm 10
    :microbial-load-max-cfu 1000}

   :starch/cassava-native
   {:id :starch/cassava-native
    :name "タピオカでん粉（ネイティブ）"
    :moisture-target-percent 13.0
    :moisture-tolerance-percent 0.5
    :purity-min-percent 98.5
    :purity-max-percent 100.0
    :granulation-min-microns 5
    :granulation-max-microns 35
    :sulfite-residue-max-ppm 10
    :microbial-load-max-cfu 500}

   :starch/wheat-native
   {:id :starch/wheat-native
    :name "小麦でん粉（ネイティブ）"
    :moisture-target-percent 14.0
    :moisture-tolerance-percent 0.5
    :purity-min-percent 98.0
    :purity-max-percent 100.0
    :granulation-min-microns 5
    :granulation-max-microns 30
    :sulfite-residue-max-ppm 50
    :microbial-load-max-cfu 1000}})

(defn product-type-by-id [id]
  (get product-types id))

(def jurisdictions
  "Starch-products jurisdictions and their food-safety-declaration and
  evidence-checklist requirements."
  {:jp/prefectural
   {:id :jp/prefectural
    :name "日本 (食品表示法・都道府県)"
    :allergen-declaration-required true
    :major-allergens #{:wheat}
    :required-evidence
    [:raw-material-intake-record
     :extraction-log
     :moisture-test
     :purity-test
     :sulfite-residue-test
     :microbial-test
     :allergen-declaration
     :weight-check]}

   :us/fda
   {:id :us/fda
    :name "United States (FDA/FALCPA)"
    :allergen-declaration-required true
    :major-allergens #{:wheat}
    :required-evidence
    [:raw-material-intake-record
     :extraction-log
     :moisture-test
     :purity-test
     :sulfite-residue-test
     :microbial-test
     :allergen-declaration
     :weight-check]}

   :eu/efsa
   {:id :eu/efsa
    :name "European Union (EFSA)"
    :allergen-declaration-required true
    :major-allergens #{:wheat}
    :required-evidence
    [:raw-material-intake-record
     :extraction-log
     :moisture-test
     :purity-test
     :sulfite-residue-test
     :microbial-test
     :allergen-declaration
     :weight-check]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(def raw-material-allergen-table
  "Per-raw-material primary allergen and cross-contact risk, used to derive
  an extraction batch's allergen set for label-accuracy verification. Corn,
  potato, and cassava starch are naturally gluten-free at the raw-material
  level; wheat starch is the one common feedstock in this ISIC class that
  retains a genuine `:wheat` (gluten) allergen in the finished product
  unless it has been through additional gluten-removal processing this
  actor does not track. `:corn/waxy-hybrid` (used for some modified
  starches) carries no primary allergen of its own but, like the reference
  grain-mill actor's shared-line oat, carries a real-world `:wheat`
  cross-contact risk when milled/extracted on equipment shared with wheat
  starch. Raw materials with no allergen relevance map to nil."
  {:corn/yellow-dent    {:primary-allergen nil :cross-contact-risk #{}}
   :corn/waxy-hybrid    {:primary-allergen nil :cross-contact-risk #{:wheat}}
   :potato/starch-grade {:primary-allergen nil :cross-contact-risk #{}}
   :cassava/root        {:primary-allergen nil :cross-contact-risk #{}}
   :wheat/starch-grade  {:primary-allergen :wheat :cross-contact-risk #{}}})

(defn raw-material-allergens [id]
  (get raw-material-allergen-table id))

(defn raw-material-allergen-set
  "Given an extraction batch's raw-material-id list, return the set of
  primary allergens actually present. Non-allergenic / unknown raw-material
  ids contribute nothing."
  [raw-materials]
  (into #{}
        (keep (fn [id] (:primary-allergen (raw-material-allergens id))))
        raw-materials))

(defn allergen-declaration-complete?
  "Verify that `declared` allergens are a superset of the batch's actual
  allergens for `raw-materials`. Extra (conservative) declarations pass;
  omissions fail. `jurisdiction` is accepted for call-site symmetry with
  other facts lookups."
  [_jurisdiction raw-materials declared]
  (set/subset? (raw-material-allergen-set raw-materials) (set declared)))

(defn required-evidence-satisfied?
  "Verify that every item in the jurisdiction's `:required-evidence` list
  is present in `evidence`. `jurisdiction` may be a resolved jurisdiction
  map (as returned by `jurisdiction-by-id`) or a raw jurisdiction id --
  both call conventions are in use (tests pass a resolved map; the
  Governor passes the raw id straight off batch metadata)."
  [jurisdiction evidence]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (if-not j
      false
      (set/subset? (set (:required-evidence j)) (set evidence)))))

(defn moisture-in-range?
  "Positive-sense convenience predicate: does `percent` fall within
  `product`'s moisture tolerance window (inclusive) around its target?
  Starch products must stay within a narrow moisture band -- too high
  risks microbial/mold growth in storage, too low degrades yield and
  flowability of the finished powder."
  [percent product]
  (boolean
   (and (some? product)
        (let [target (:moisture-target-percent product)
              tol (:moisture-tolerance-percent product)]
          (and (>= percent (- target tol))
               (<= percent (+ target tol)))))))

(defn purity-in-range?
  "Positive-sense convenience predicate: does `percent` fall within
  `product`'s expected purity window (inclusive)?"
  [percent product]
  (boolean
   (and (some? product)
        (>= percent (:purity-min-percent product))
        (<= percent (:purity-max-percent product)))))

(defn granulation-in-range?
  "Positive-sense convenience predicate: does `microns` fall within
  `product`'s expected particle-size window (inclusive)?"
  [microns product]
  (boolean
   (and (some? product)
        (>= microns (:granulation-min-microns product))
        (<= microns (:granulation-max-microns product)))))

(defn sulfite-residue-in-range?
  "Positive-sense convenience predicate: does `ppm` stay at or below
  `product`'s maximum allowable sulfite (SO2) residue?"
  [ppm product]
  (boolean
   (and (some? product)
        (<= ppm (:sulfite-residue-max-ppm product)))))

(defn microbial-load-in-range?
  "Positive-sense convenience predicate: does `cfu` stay at or below
  `product`'s maximum allowable microbial colony count?"
  [cfu product]
  (boolean
   (and (some? product)
        (<= cfu (:microbial-load-max-cfu product)))))
