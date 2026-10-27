-- DIV-1. Position bestehender Einkaufslisten (je Gruppe) und Einträge (je Liste) aus der bisherigen Anzeigereihenfolge
-- nach id befüllen, beginnend bei 0.
-- Wird auch von SortOrderBackfillIntegrationSpec als Native Query ausgeführt. Deshalb Anweisungen nur durch
-- Semikolon am Zeilenende trennen, in Kommentaren und Strings weder Semikolon noch Doppelpunkt verwenden
-- und Casts mit CAST schreiben (Hibernate hält Doppelpunkt-Namen für Parameter).

UPDATE shoppinglist s SET sort_order = r.rn
FROM (SELECT id, CAST(row_number() OVER (PARTITION BY group_id ORDER BY id) - 1 AS integer) AS rn FROM shoppinglist) r
WHERE s.id = r.id;

UPDATE shoppingitem i SET sort_order = r.rn
FROM (SELECT id, CAST(row_number() OVER (PARTITION BY shoppinglist_id ORDER BY id) - 1 AS integer) AS rn FROM shoppingitem) r
WHERE i.id = r.id;
