-- V2__add_user_id_to_product.sql
-- Add user_id column to product table if not exists

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name='product' AND column_name='user_id'
    ) THEN
        ALTER TABLE product ADD COLUMN user_id BIGINT;
        RAISE NOTICE 'Added user_id column to product table';
    END IF;
END $$;

-- Assign existing products to the first available user to avoid null pointer issues
DO $$
DECLARE
    first_user_id BIGINT;
BEGIN
    -- Check "users" table first
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='users') THEN
        SELECT id INTO first_user_id FROM users LIMIT 1;
    -- Fallback to "user" table
    ELSIF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='user') THEN
        SELECT id INTO first_user_id FROM "user" LIMIT 1;
    END IF;

    IF first_user_id IS NOT NULL THEN
        UPDATE product SET user_id = first_user_id WHERE user_id IS NULL;
        RAISE NOTICE 'Updated existing products with user_id %', first_user_id;
    END IF;
END $$;

-- Add foreign key constraint if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_name='fk_user' AND table_name='product'
    ) THEN
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='users') THEN
            ALTER TABLE product ADD CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
            RAISE NOTICE 'Created foreign key constraint referencing users table';
        ELSIF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='user') THEN
            ALTER TABLE product ADD CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE;
            RAISE NOTICE 'Created foreign key constraint referencing user table';
        END IF;
    END IF;
END $$;
