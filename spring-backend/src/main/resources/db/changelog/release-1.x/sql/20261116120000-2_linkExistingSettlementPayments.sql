-- DIV-74: Verknüpfung bestehender Ausgleichszahlungen (Changeset 20261116120000-2).
-- Wird auch von SettlementLinkMigrationIntegrationSpec ausgeführt – als ein einziger Befehl (DO-Block), daher keine
-- Semikolon-Aufteilung.
--
-- Kandidaten: Carts in gekennzeichneten Kategorien (nach Schritt 1) ohne settlement_id.
-- Schlüssel K = (group_id, category_id, date_purchased, abs(amount)). Je K höchstens zwei User.
-- Paarbildung richtungsbezogen: positive Carts werden je (K, User) nach Id nummeriert, negative ebenso; Paar = positiver
-- Cart von User U mit Nummer i und negativer Cart eines anderen Users V mit Nummer i im selben K. Bei zwei Usern ist V
-- eindeutig. Zahler = User des positiven Carts, Empfänger = User des negativen Carts, Betrag = Betrag des positiven Carts.
-- Vor dem ersten Insert wird geprüft; jeder Abbruch rollt das ganze Changeset zurück.
DO $$
DECLARE
    zu_viele_user integer;
    schluessel_info text;
    ohne_partner integer;
    ohne_partner_ids text;
BEGIN
    UPDATE category SET is_settlement = true
     WHERE lower(trim(name)) IN ('ausgleichszahlung', 'repayment', 'repago')
       AND is_settlement = false;

    DROP TABLE IF EXISTS div74_kandidaten;
    CREATE TEMP TABLE div74_kandidaten ON COMMIT DROP AS
    SELECT x.id, x.group_id, x.category_id, x.date_purchased, x.amount, x.user_id, x.date_created
      FROM cart x
      JOIN category c ON c.id = x.category_id
     WHERE c.is_settlement
       AND x.settlement_id IS NULL;

    SELECT count(*), string_agg(info, '; ')
      INTO zu_viele_user, schluessel_info
      FROM (SELECT 'Gruppe ' || group_id || ', Kategorie ' || category_id || ', ' || date_purchased || ', ' || abs(amount)
                   || ' (Carts ' || string_agg(id::text, ',' ORDER BY id) || ')' AS info
              FROM div74_kandidaten
             GROUP BY group_id, category_id, date_purchased, abs(amount)
            HAVING count(DISTINCT user_id) > 2) s;
    IF zu_viele_user > 0 THEN
        RAISE EXCEPTION 'DIV-74: % Schlüssel mit mehr als zwei Beteiligten (nicht unterstützt): %',
            zu_viele_user, schluessel_info;
    END IF;

    DROP TABLE IF EXISTS div74_paare;
    CREATE TEMP TABLE div74_paare ON COMMIT DROP AS
    WITH nummeriert AS (
        SELECT k.*,
               row_number() OVER (PARTITION BY group_id, category_id, date_purchased, abs(amount), user_id, sign(amount)
                                  ORDER BY id) AS nr
          FROM div74_kandidaten k
         WHERE amount <> 0
    )
    SELECT p.id AS pos_id, n.id AS neg_id, p.group_id, p.user_id AS payer_id, n.user_id AS receiver_id,
           p.amount, p.date_purchased, least(p.date_created, n.date_created) AS date_created,
           CAST(NULL AS bigint) AS settlement_id
      FROM nummeriert p
      JOIN nummeriert n
        ON n.group_id = p.group_id
       AND n.category_id = p.category_id
       AND n.date_purchased = p.date_purchased
       AND abs(n.amount) = abs(p.amount)
       AND p.amount > 0
       AND n.amount < 0
       AND n.user_id <> p.user_id
       AND n.nr = p.nr;

    SELECT count(*), string_agg(id::text, ',' ORDER BY id)
      INTO ohne_partner, ohne_partner_ids
      FROM div74_kandidaten k
     WHERE NOT EXISTS (SELECT 1 FROM div74_paare p WHERE k.id IN (p.pos_id, p.neg_id));
    IF ohne_partner > 0 THEN
        RAISE EXCEPTION 'DIV-74: % Ausgleichszahlungs-Carts ohne eindeutigen Partner (Betrag 0, gleicher User, Waise oder abweichend): Carts %',
            ohne_partner, ohne_partner_ids;
    END IF;

    UPDATE div74_paare SET settlement_id = nextval(pg_get_serial_sequence('settlement', 'id'));

    INSERT INTO settlement (id, group_id, payer_id, receiver_id, created_by, amount, date_purchased, date_created, last_update)
    SELECT settlement_id, group_id, payer_id, receiver_id, NULL, amount, date_purchased, date_created, now()
      FROM div74_paare;

    UPDATE cart c
       SET settlement_id = p.settlement_id
      FROM div74_paare p
     WHERE c.id IN (p.pos_id, p.neg_id);
END
$$;
