# ARK V25.2 — Full Continuity Fix

The smaller V25.1 APK exposed a real merge defect. A V24 UI merge accidentally removed a large block of V23 JavaScript functions while leaving the route names in the switch. JavaScript syntax still passed, so earlier static checks did not catch it.

V25.2 starts from the complete V23 app.js, then adds V24/V25 features additively. It restores the V23 Home/Understand/Live/Alexandria/Humanity/Creation/Ademic/Runes/Meaning/Patents/Technology/Family Gifts/Techniques/Jesus/Study/Worksheets/Church/Tradition/Ancestors/Notes logic and its helper functions, while preserving Phone Intake, Unified Core, Global Search, Universal Text Viewer, Route Atlas, Home Base recovery, Kernel R2, and the permanent ARK identity.

A new runtime-continuity verifier now fails the build if any route renderer or required V23 function disappears.
