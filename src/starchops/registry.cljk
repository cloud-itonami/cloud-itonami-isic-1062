(ns starchops.registry
  "Pure validation functions for starches-and-starch-products production
  parameters. These are called by the Governor to independently verify
  physical/operational constraints -- the advisor's confidence is NOT
  sufficient to override these checks.

  All functions here are pure arithmetic/set/boolean predicates with no
  host-clock or I/O calls, so this namespace stays trivially portable
  across Clojure/ClojureScript. Callers that need the current time (see
  `detection-equipment-calibration-overdue?`) obtain it themselves via a
  `:clj`/`:cljs` reader-conditional at the call site (see
  `starchops.governor`)."
  (:require [clojure.set :as set]))

(defn moisture-out-of-target?
  "Independently verify that the batch's finished-product moisture falls
  within tolerance of the product's target moisture. Starch products
  outside their moisture window risk microbial/mold growth in storage (too
  high) or degraded yield/powder flowability (too low)."
  [actual-percent target-percent tolerance-percent]
  (or (< actual-percent (- target-percent tolerance-percent))
      (> actual-percent (+ target-percent tolerance-percent))))

(defn sulfite-residue-exceeded?
  "Independently verify that the batch's actual sulfite (SO2) residue (ppm)
  does not exceed the product's maximum allowable level. Residual sulfur
  dioxide from the steeping/bleaching step is a regulated food-safety
  hazard -- levels above the product's action level are a hard,
  un-overridable stop."
  [actual-ppm max-ppm]
  (> actual-ppm max-ppm))

(defn microbial-load-exceeded?
  "Independently verify that the batch's actual microbial colony count
  (CFU/g) does not exceed the product's maximum allowable level. Starch
  slurries sit at high moisture and near-neutral pH for extended dwell
  times during extraction/refining, making microbial/pathogen growth a
  genuine, product-type-specific food-safety hazard."
  [actual-cfu max-cfu]
  (> actual-cfu max-cfu))

(defn purity-out-of-range?
  "Independently verify that the batch's purity (100 minus residual
  protein/fiber/fat left over from incomplete separation) falls within the
  product's expected range [min,max]. Both bounds are inclusive of the
  in-range case; out-of-range indicates incomplete extraction (too low) or
  a mislabeled/misclassified product grade (too high)."
  [actual-percent min-percent max-percent]
  (or (< actual-percent min-percent)
      (> actual-percent max-percent)))

(defn granulation-out-of-range?
  "Independently verify that the batch's particle-size distribution
  (microns) falls within the product's expected range. Granulation
  outside range indicates a classifier/dryer fault and risks
  misclassifying the product grade."
  [actual-microns min-microns max-microns]
  (or (< actual-microns min-microns)
      (> actual-microns max-microns)))

(defn detection-equipment-calibration-overdue?
  "Independently verify that the foreign-material-detection equipment
  (magnet/metal detector/optical sorter guarding the extraction and
  refining lines) was calibrated within the last 60 days. A shorter
  interval than the reference grain-mill actor's 90-day interval reflects
  the higher fouling/drift rate of detection equipment running
  continuously on a high-moisture wet-processing line (steeping, wet
  milling, centrifugal refining) rather than a dry milling line.
  `last-calibration-epoch-ms` and `now-epoch-ms` are both epoch
  milliseconds -- callers obtain `now` via a `:clj`/`:cljs`
  reader-conditional, keeping this namespace free of any host-clock call."
  [last-calibration-epoch-ms now-epoch-ms]
  (> (- now-epoch-ms last-calibration-epoch-ms)
     (* 60 24 60 60 1000)))

(defn weight-variance-excessive?
  "Independently verify that a batch's finished-product weight variance
  (drift from target, in grams) does not exceed the maximum tolerance.
  Excessive variance indicates the packaging scale is out of calibration
  or the extraction yield was measured incorrectly."
  [actual-variance-grams max-variance-grams]
  (> actual-variance-grams max-variance-grams))

(defn allergen-label-risk?
  "True when the batch's raw-material formulation contains an allergen NOT
  present in the declared-allergens set (mislabeling / under-declaration
  risk -- a genuine food-safety hazard for allergic consumers, and
  especially for gluten-free-labeled corn/potato/cassava-starch products
  extracted or packaged on shared lines with wheat starch). Declaring MORE
  allergens than the batch actually contains is conservative and never a
  risk."
  [formula-allergens declared-allergens]
  (not (set/subset? (set formula-allergens) (set declared-allergens))))

(defn foreign-material-detected?
  "Independently verify a batch's foreign-material-detection result
  (metal, stone, glass, or insect fragments caught by magnet/sifter/
  optical-sorter inspection). Any detection is a genuine physical hazard
  -- this predicate simply coerces the raw fact to a boolean so the
  Governor's check functions stay uniform in shape with every other
  independently-verified physical constraint in this namespace."
  [actual-detected?]
  (boolean actual-detected?))

(defn sanitation-score-insufficient?
  "Independently verify that the plant's pre-production sanitation/
  pest-control score meets the minimum required. Score is 0-100, assessed
  by a third-party auditor against food-safety sanitation standards -- a
  significant HACCP concern specific to wet starch extraction/refining,
  where standing slurry and washwater are a real microbial-growth
  environment."
  [actual-score min-score-required]
  (< actual-score min-score-required))
