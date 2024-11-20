#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <kprintf/kprintf.h>

#define CHACHA_TRIGGER 0x00
#define CHACHA_DATA_A 0x04
#define CHACHA_DATA_B 0x08
#define CHACHA_DATA_C 0x0C

#define CHACHA_ADDR_CTRL           0x08
#define CHACHA_ADDR_STATUS         0x09
#define CHACHA_ADDR_KEYLEN         0x0a
#define CHACHA_ADDR_ROUNDS         0x0b

#define CHACHA_KEYLEN_128 		   0x00
#define CHACHA_KEYLEN_256 		   0x01

#define CHACHA_ADDR_KEY_BASE       0x10     // 0x10 -> 0x17

#define CHACHA_ADDR_IV0            0x20
#define CHACHA_ADDR_IV1            0x21

#define CHACHA_ADDR_DATA_IN_BASE   0x40     // 0x40 -> 0x4F
#define CHACHA_ADDR_DATA_OUT_BASE  0x80     // 0x80 -> 0x8F

void chacha_write_to_address(uint32_t address, uint32_t data, unsigned long chacha_reg)
{
  _REG32(chacha_reg, CHACHA_DATA_A) = address;
  _REG32(chacha_reg, CHACHA_DATA_B) = data;
  _REG32(chacha_reg, CHACHA_TRIGGER) = 0x00010101;
  _REG32(chacha_reg, CHACHA_TRIGGER) = 0x00010000;
}

uint32_t chacha_read_from_address(uint32_t address, unsigned long chacha_reg)
{
  _REG32(chacha_reg, CHACHA_DATA_A) = address;
  _REG32(chacha_reg, CHACHA_DATA_B) = 0;
  _REG32(chacha_reg, CHACHA_TRIGGER) = 0x00010001;
  uint32_t temp = _REG32(chacha_reg, CHACHA_DATA_C);
  _REG32(chacha_reg, CHACHA_TRIGGER) = 0x00010000;
  return temp;
}

void chacha_cipher(
        uint32_t key_len,
        uint32_t *key,
        uint32_t *iv,
        uint32_t num_round,
        uint32_t *data_in,
        uint32_t *data_out,
        unsigned long chacha_reg)
{
    for (int i = 0; i < 8; ++i)
        chacha_write_to_address(CHACHA_ADDR_KEY_BASE + i, key[i], chacha_reg);

    for (int i = 0; i < 16; ++i)
        chacha_write_to_address(CHACHA_ADDR_DATA_IN_BASE + i, data_in[i], chacha_reg);

    chacha_write_to_address(CHACHA_ADDR_IV0, iv[0], chacha_reg);
    chacha_write_to_address(CHACHA_ADDR_IV1, iv[1], chacha_reg);

    chacha_write_to_address(CHACHA_ADDR_KEYLEN, key_len, chacha_reg);
    chacha_write_to_address(CHACHA_ADDR_ROUNDS, num_round, chacha_reg);
    chacha_write_to_address(CHACHA_ADDR_CTRL, 0x01, chacha_reg);

    while(chacha_read_from_address(CHACHA_ADDR_STATUS, chacha_reg) == 0);

    for (int i = 0; i < 16; ++i)
        data_out[i] = chacha_read_from_address(CHACHA_ADDR_DATA_OUT_BASE + i, chacha_reg);
}

void chacha_cipher_next_block(uint32_t *data_out, unsigned long chacha_reg) {
    chacha_write_to_address(CHACHA_ADDR_CTRL, 0x02, chacha_reg);

    while(chacha_read_from_address(CHACHA_ADDR_STATUS, chacha_reg) == 0);

    for (int i = 0; i < 16; ++i)
        data_out[i] = chacha_read_from_address(CHACHA_ADDR_DATA_OUT_BASE + i, chacha_reg);
}

void chacha_cmd(unsigned long chacha_reg) {
    uint32_t chacha_key[8] 	= {0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0};
	uint32_t chacha_iv[2] 	= {0x0, 0x0};
	uint32_t chacha_data_in[16] 	= {
			0x0, 0x0, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x0};

	uint32_t chacha_dump[16] 	= {
			0x0, 0x0, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x0,
			0x0, 0x0, 0x0, 0x0};

	uint32_t chacha_expected[16] 	= {
			0xe28a5fa4, 0xa67f8c5d, 0xefed3e6f, 0xb7303486,
			0xaa8427d3, 0x1419a729, 0x572d7779, 0x53491120,
			0xb64ab8e7, 0x2b8deb85, 0xcd6aea7c, 0xb6089a10,
			0x1824beeb, 0x08814a42, 0x8aab1fa2, 0xc816081b};




    // ================================= TEST ONE =================================

    chacha_cipher(CHACHA_KEYLEN_128, chacha_key, chacha_iv, 0x08, chacha_data_in, chacha_dump, chacha_reg);

    kprintf("\r\n# ChaCha - 8 rounds, 128-bit key ============================================\r\n");
	kprintf("Key:             ");
    for(int i = 0; i < 8; i++) {
        kprintf("%lx", chacha_key[i]);
    }
    kprintf("\r\n");

    kprintf("IV (nonce):      ");
    for(int i = 0; i < 2; i++) {
        kprintf("%lx", chacha_iv[i]);
    }
    kprintf("\r\n");

    kprintf("Expected result: ");
    for(int i = 0; i < 16; i++) {
        kprintf("%lx", chacha_expected[i]);
    }
	kprintf("\r\n");

	kprintf("Result dump:     ");
	for(int i = 0; i < 16; i++) {
	    kprintf("%lx", chacha_dump[i]);
	}
    kprintf("\r\n\n");



    // ================================= TEST TWO =================================
    chacha_key[0] = 0x00;
	chacha_key[1] = 0x00;
	chacha_key[2] = 0x00;
	chacha_key[3] = 0x00;
	chacha_key[4] = 0x00;
	chacha_key[5] = 0x00;
	chacha_key[6] = 0x00;
	chacha_key[7] = 0x00;

	chacha_iv[0] = 0x00;
	chacha_iv[1] = 0x00;

    chacha_expected[0] = 0x76b8e0ad;
    chacha_expected[1] = 0xa0f13d90;
    chacha_expected[2] = 0x405d6ae5;
    chacha_expected[3] = 0x5386bd28;
    chacha_expected[4] = 0xbdd219b8;
    chacha_expected[5] = 0xa08ded1a;
    chacha_expected[6] = 0xa836efcc;
    chacha_expected[7] = 0x8b770dc7;
    chacha_expected[8] = 0xda41597c;
    chacha_expected[9] = 0x5157488d;
    chacha_expected[10] = 0x7724e03f;
    chacha_expected[11] = 0xb8d84a37;
    chacha_expected[12] = 0x6a43b8f4;
    chacha_expected[13] = 0x1518a11c;
    chacha_expected[14] = 0xc387b669;
    chacha_expected[15] = 0xb2ee6586;

    chacha_cipher(CHACHA_KEYLEN_256, chacha_key, chacha_iv, 0x14, chacha_data_in, chacha_dump, chacha_reg);

    kprintf("# ChaCha - 20 rounds, 256-bit key ============================================\r\n");
    kprintf("Key:             ");
    for(int i = 0; i < 8; i++) {
        kprintf("%lx", chacha_key[i]);
    }
    kprintf("\r\n");

    kprintf("IV (nonce):      ");
    for(int i = 0; i < 2; i++) {
        kprintf("%lx", chacha_iv[i]);
    }
    kprintf("\r\n");

    kprintf("Expected result: ");
    for(int i = 0; i < 16; i++) {
        kprintf("%lx", chacha_expected[i]);
    }

    kprintf("\r\n");
    kprintf("Result dump:     ");
    for(int i = 0; i < 16; i++) {
        kprintf("%lx", chacha_dump[i]);
    }
    kprintf("\r\n\n");




    // ================================= TEST THREE =================================
    chacha_cipher_next_block(chacha_dump, chacha_reg);

    kprintf("# ChaCha - 20 rounds, 256-bit key, next block ================================\r\n");
	kprintf("Expected result: 9f07e7be5551387a98ba977c732d080dcb0f29a048e3656912c6533e32ee7aed29b721769ce64e43d57133b074d839d531ed1f28510afb45ace10a1f4b794d6f");
	kprintf("\r\n");
	kprintf("Result dump:     ");
	for(int i = 0; i < 16; i++) {
	    kprintf("%lx", chacha_dump[i]);
	}
    kprintf("\r\n\n");
}