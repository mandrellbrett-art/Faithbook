-- HeritageFaith Legacy Patent Inventory
-- Google Patents Public Data / BigQuery.
-- Export the result as CSV, then import it into:
-- HeritageFaith -> Patents -> Import legacy patent inventory
--
-- Adjust the cutoff if you run this after 2026-09-07.
-- This example uses the current 70-year cutoff: 1956-09-07.

SELECT
  p.publication_number,
  COALESCE(
    (SELECT ANY_VALUE(t.text) FROM UNNEST(p.title_localized) t WHERE t.language = 'en'),
    (SELECT ANY_VALUE(t.text) FROM UNNEST(p.title_localized) t)
  ) AS title,
  CAST(p.grant_date AS STRING) AS grant_date,
  CAST(p.filing_date AS STRING) AS filing_date,
  CAST(p.priority_date AS STRING) AS priority_date,
  p.country_code AS jurisdiction,
  ARRAY_TO_STRING(ARRAY(SELECT c.code FROM UNNEST(p.cpc) c LIMIT 12), '; ') AS classification
FROM `patents-public-data.patents.publications` p
WHERE p.grant_date > 0
  AND p.grant_date < 19560907
ORDER BY p.grant_date, p.publication_number;
