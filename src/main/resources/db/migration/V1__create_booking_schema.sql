CREATE TABLE admins (
  id VARCHAR(36) PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE bookings (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  phone VARCHAR(10) NOT NULL,
  booking_date DATE NOT NULL,
  booking_time VARCHAR(5) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'waiting',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT booking_unique_slot UNIQUE (booking_date, booking_time),
  CONSTRAINT booking_status_valid CHECK (status IN ('waiting', 'cutting', 'done')),
  CONSTRAINT booking_time_valid CHECK (booking_time IN ('10:00','10:30','11:00','11:30','12:00','12:30','13:00','13:30','14:00')),
  CONSTRAINT booking_phone_valid CHECK (phone ~ '^0[689][0-9]{8}$')
);