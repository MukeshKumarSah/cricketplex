-- =============================================
-- V2: Add ground_name and team_profile_pic_url to teams
-- =============================================

ALTER TABLE teams ADD COLUMN ground_name VARCHAR(255);
ALTER TABLE teams ADD COLUMN team_profile_pic_url VARCHAR(512);
