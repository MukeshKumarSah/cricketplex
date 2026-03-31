-- Add UTC match start time for each league (derived from country)
ALTER TABLE leagues ADD COLUMN match_start_time VARCHAR(5);

UPDATE leagues SET match_start_time = CASE country
    WHEN 'New Zealand'          THEN '07:00'
    WHEN 'Australia'            THEN '09:00'
    WHEN 'Bangladesh'           THEN '13:00'
    WHEN 'Nepal'                THEN '13:15'
    WHEN 'Sri Lanka'            THEN '13:30'
    WHEN 'India'                THEN '14:30'
    WHEN 'Pakistan'             THEN '15:00'
    WHEN 'Afghanistan'          THEN '15:30'
    WHEN 'Oman'                 THEN '16:00'
    WHEN 'United Arab Emirates' THEN '16:30'
    WHEN 'South Africa'         THEN '17:00'
    WHEN 'Zimbabwe'             THEN '17:30'
    WHEN 'Netherlands'          THEN '18:00'
    WHEN 'Scotland'             THEN '18:30'
    WHEN 'Ireland'              THEN '19:00'
    WHEN 'England'              THEN '19:30'
    WHEN 'West Indies'          THEN '23:00'
    WHEN 'United States'        THEN '00:00'
    ELSE '14:00'
END;

ALTER TABLE leagues ALTER COLUMN match_start_time SET NOT NULL;
