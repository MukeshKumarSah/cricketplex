export const COUNTRIES = [
  'Afghanistan', 'Australia', 'Bangladesh', 'England', 'India',
  'Ireland', 'Nepal', 'Netherlands', 'New Zealand', 'Oman',
  'Pakistan', 'Scotland', 'South Africa', 'Sri Lanka',
  'United Arab Emirates', 'United States', 'West Indies', 'Zimbabwe',
];

/**
 * UTC match start times per country (evening prime-time local).
 * Distributed across the day to spread server load.
 */
export const COUNTRY_MATCH_TIMES = {
  'Afghanistan':          '15:30',
  'Australia':            '09:00',
  'Bangladesh':           '13:00',
  'England':              '19:30',
  'India':                '14:30',
  'Ireland':              '19:00',
  'Nepal':                '13:15',
  'Netherlands':          '18:00',
  'New Zealand':          '07:00',
  'Oman':                 '16:00',
  'Pakistan':             '15:00',
  'Scotland':             '18:30',
  'South Africa':         '17:00',
  'Sri Lanka':            '13:30',
  'United Arab Emirates': '16:30',
  'United States':        '00:00',
  'West Indies':          '23:00',
  'Zimbabwe':             '17:30',
};
