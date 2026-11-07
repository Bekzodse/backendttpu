-- V14__seed_default_reference_and_qr_items.sql
-- Seeds baseline TTPU reference entities and the 5 public QR items used by load-test.js
-- Uses ON CONFLICT DO NOTHING so re-runs and test suites remain idempotent.

SET TIME ZONE 'UTC';

INSERT INTO buildings (id, code, name, address, description, active)
VALUES (
    '00000000-0000-0000-0000-000000000101',
    'BLD-TTPU-MAIN',
    'TTPU Main Academic Building',
    'Tashkent, Kichik Halqa Yo''li 17',
    'Turin Polytechnic University in Tashkent Main Campus',
    TRUE
) ON CONFLICT (code) DO NOTHING;

INSERT INTO floors (id, building_id, floor_number, name, active)
VALUES (
    '00000000-0000-0000-0000-000000000102',
    '00000000-0000-0000-0000-000000000101',
    1,
    '1st Floor',
    TRUE
) ON CONFLICT (building_id, floor_number) DO NOTHING;

INSERT INTO departments (id, code, name, parent_id, building_id, active)
VALUES (
    '00000000-0000-0000-0000-000000000103',
    'DEP-TTPU-IT',
    'Information Technology & Digital Inventory Center',
    NULL,
    '00000000-0000-0000-0000-000000000101',
    TRUE
) ON CONFLICT (code) DO NOTHING;

INSERT INTO rooms (id, floor_id, department_id, room_number, name, capacity, active)
VALUES (
    '00000000-0000-0000-0000-000000000104',
    '00000000-0000-0000-0000-000000000102',
    '00000000-0000-0000-0000-000000000103',
    'A-101',
    'Central IT Inventory Room',
    25,
    TRUE
) ON CONFLICT (floor_id, room_number) DO NOTHING;

INSERT INTO employees (
    id, employee_code, first_name, last_name, middle_name,
    email, phone, position, department_id, room_id, hemis_id, one_c_id, active
)
VALUES (
    '00000000-0000-0000-0000-000000000105',
    'EMP-TTPU-0001',
    'Rustam',
    ' Karimov',
    'Alisherovich',
    'inventory.admin@ttpu.uz',
    '+998712467082',
    'Chief Inventory Specialist',
    '00000000-0000-0000-0000-000000000103',
    '00000000-0000-0000-0000-000000000104',
    'HEMIS-ADMIN-001',
    '1C-ADMIN-001',
    TRUE
) ON CONFLICT (employee_code) DO NOTHING;

INSERT INTO categories (id, code, name, parent_id, depreciation_years, custom_fields, active)
VALUES (
    '00000000-0000-0000-0000-000000000106',
    'CAT-TTPU-COMP',
    'Computing & Laboratory Equipment',
    NULL,
    5,
    '{"warrantyMonths": 36, "trackSerial": true}'::jsonb,
    TRUE
) ON CONFLICT (code) DO NOTHING;

INSERT INTO items (
    id, inventory_number, serial_number, qr_code, name, description,
    category_id, state, purchase_price, residual_value, commissioned_at,
    building_id, floor_id, room_id, department_id, custodian_employee_id,
    custom_attributes, one_c_ref_id
)
VALUES
    (
        '00000000-0000-0000-0000-000000000201',
        'TTPU-INV-000001',
        'SN-TTPU-000001',
        'JBSWY3DPEB',
        'Dell Precision 3680 Tower Workstation',
        'CAD/CAM Lab Workstation #1',
        '00000000-0000-0000-0000-000000000106',
        'FOYDALANISHDA',
        28500000.00,
        25650000.00,
        CURRENT_TIMESTAMP,
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000104',
        '00000000-0000-0000-0000-000000000103',
        '00000000-0000-0000-0000-000000000105',
        '{"cpu": "Intel Core i9", "ramGb": 64}'::jsonb,
        '1C-SEED-001'
    ),
    (
        '00000000-0000-0000-0000-000000000202',
        'TTPU-INV-000002',
        'SN-TTPU-000002',
        'KRSXG5BAON',
        'MacBook Pro 16 M3 Pro',
        'Faculty Research Laptop #2',
        '00000000-0000-0000-0000-000000000106',
        'FOYDALANISHDA',
        36000000.00,
        32400000.00,
        CURRENT_TIMESTAMP,
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000104',
        '00000000-0000-0000-0000-000000000103',
        '00000000-0000-0000-0000-000000000105',
        '{"cpu": "Apple M3 Pro", "ramGb": 36}'::jsonb,
        '1C-SEED-002'
    ),
    (
        '00000000-0000-0000-0000-000000000203',
        'TTPU-INV-000003',
        'SN-TTPU-000003',
        'MZXW6YTBOI',
        'Epson EB-L530U Laser Projector',
        'Lecture Hall Laser Projector #3',
        '00000000-0000-0000-0000-000000000106',
        'FOYDALANISHDA',
        24000000.00,
        21600000.00,
        CURRENT_TIMESTAMP,
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000104',
        '00000000-0000-0000-0000-000000000103',
        '00000000-0000-0000-0000-000000000105',
        '{"lumens": 5200}'::jsonb,
        '1C-SEED-003'
    ),
    (
        '00000000-0000-0000-0000-000000000204',
        'TTPU-INV-000004',
        'SN-TTPU-000004',
        'NBSWY3DPF4',
        'Tektronix TBS2000B Digital Oscilloscope',
        'Mechatronics Lab Oscilloscope #4',
        '00000000-0000-0000-0000-000000000106',
        'FOYDALANISHDA',
        19500000.00,
        17550000.00,
        CURRENT_TIMESTAMP,
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000104',
        '00000000-0000-0000-0000-000000000103',
        '00000000-0000-0000-0000-000000000105',
        '{"bandwidthMhz": 200}'::jsonb,
        '1C-SEED-004'
    ),
    (
        '00000000-0000-0000-0000-000000000205',
        'TTPU-INV-000005',
        'SN-TTPU-000005',
        'ORSXG5BAMN',
        'Cisco Catalyst 9300 48-Port Switch',
        'Core Campus Network Switch #5',
        '00000000-0000-0000-0000-000000000106',
        'FOYDALANISHDA',
        45000000.00,
        40500000.00,
        CURRENT_TIMESTAMP,
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000102',
        '00000000-0000-0000-0000-000000000104',
        '00000000-0000-0000-0000-000000000103',
        '00000000-0000-0000-0000-000000000105',
        '{"ports": 48}'::jsonb,
        '1C-SEED-005'
    )
ON CONFLICT (inventory_number) DO NOTHING;
