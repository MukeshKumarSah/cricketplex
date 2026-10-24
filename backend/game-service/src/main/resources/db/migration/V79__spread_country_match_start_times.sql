-- Spread country league kickoffs to at least 1 hour apart (UTC).
-- Local time stays between 07:00 and 20:00 in standard and DST offsets.
UPDATE leagues SET match_start_time = CASE country
    WHEN 'United States'        THEN '00:00'
    WHEN 'United Arab Emirates' THEN '03:00'
    WHEN 'New Zealand'          THEN '05:00'
    WHEN 'Zimbabwe'             THEN '06:00'
    WHEN 'Australia'            THEN '07:00'
    WHEN 'Ireland'              THEN '08:00'
    WHEN 'England'              THEN '09:00'
    WHEN 'Bangladesh'           THEN '10:00'
    WHEN 'Nepal'                THEN '11:00'
    WHEN 'Sri Lanka'            THEN '12:00'
    WHEN 'India'                THEN '13:00'
    WHEN 'Pakistan'             THEN '14:00'
    WHEN 'Afghanistan'          THEN '15:00'
    WHEN 'Oman'                 THEN '16:00'
    WHEN 'South Africa'         THEN '17:00'
    WHEN 'Netherlands'          THEN '18:00'
    WHEN 'Scotland'             THEN '19:00'
    WHEN 'West Indies'          THEN '22:00'
    ELSE match_start_time
END;
