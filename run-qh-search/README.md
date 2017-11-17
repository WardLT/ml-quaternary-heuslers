# Search for New Quaternary Heuslers

This directory contains the input files needed to search for new quaternary heusler (QH) compounds using machine learning:

- _make-model.in_: A Magpie script to train a RandomForest model on all of the data in the OQMD (including the Heusler and Quat. Heusler data). Also performs cross-validation
- _run-search-iterative.in_: A Magpie script to use the model to identify which quaternary Heusler compounds are most likely to be stable (i.e., those predicted to have a stability les than 100 meV/atom).
- _example-structures_: Templates for the QH structure. Contains all three possible polymorphs at a single compositions.

To run these two scripts, call `./run_all.bs`