-- DIV-25. Ankertag bestehender Templates befüllen und bereits gedriftete Termine korrigieren.
-- Wird auch von AnchorDayBackfillIntegrationSpec als Native Query ausgeführt. Deshalb Anweisungen nur durch
-- Semikolon am Zeilenende trennen, in Kommentaren und Strings weder Semikolon noch Doppelpunkt verwenden
-- und Casts mit CAST schreiben (Hibernate hält Doppelpunkt-Namen für Parameter).

-- MONTHLY/YEARLY: größter noch vorhandener Tag aus Einkäufen und nächstem Termin (Heuristik, siehe Plan DIV-25).
-- Übrige Typen: Tag des nächsten Termins, für die Berechnung ohne Bedeutung.
UPDATE cart_template t SET anchor_day = CASE
    WHEN t.recurrence_type IN ('MONTHLY', 'YEARLY') THEN GREATEST(
        CAST(EXTRACT(DAY FROM COALESCE(t.next_execution_date, t.start_date)) AS integer),
        COALESCE((SELECT CAST(MAX(EXTRACT(DAY FROM c.date_purchased)) AS integer) FROM cart c WHERE c.template_id = t.id), 0))
    ELSE CAST(EXTRACT(DAY FROM COALESCE(t.next_execution_date, t.start_date)) AS integer)
END;

-- Gedriftete Termine aktiver Templates innerhalb ihres Monats auf den Ankertag (bzw. das Monatsende) verschieben.
UPDATE cart_template SET next_execution_date = make_date(
        CAST(EXTRACT(YEAR FROM next_execution_date) AS integer),
        CAST(EXTRACT(MONTH FROM next_execution_date) AS integer),
        LEAST(anchor_day, CAST(EXTRACT(DAY FROM (date_trunc('month', next_execution_date) + interval '1 month - 1 day')) AS integer)))
WHERE active
  AND recurrence_type IN ('MONTHLY', 'YEARLY')
  AND EXTRACT(DAY FROM next_execution_date) < anchor_day;
