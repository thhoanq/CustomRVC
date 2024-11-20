#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <kprintf/kprintf.h>

#define KLEIN_TRIGGER 0x00
#define KLEIN_DATA_A 0x04
#define KLEIN_DATA_B 0x08
#define KLEIN_DATA_C 0x0C

#define KLEIN_ADDR_CTRL     0x00
#define KLEIN_ADDR_CONF     0x01
#define KLEIN_ADDR_STATUS   0x02

#define KLEIN_ADDR_KEY0     0x10
#define KLEIN_ADDR_KEY1     0x11

#define KLEIN_ADDR_BLOCK0   0x20
#define KLEIN_ADDR_BLOCK1   0x21

#define KLEIN_ADDR_RESULT0  0x30
#define KLEIN_ADDR_RESULT1  0x31

void klein_write_to_address(uint32_t address, uint32_t data, unsigned long klein_reg)
{
  _REG32(klein_reg, KLEIN_DATA_A) = address;
  _REG32(klein_reg, KLEIN_DATA_B) = data;
  _REG32(klein_reg, KLEIN_TRIGGER) = 0x00000101;
  _REG32(klein_reg, KLEIN_TRIGGER) = 0x00000000;
}

uint32_t klein_read_to_address(uint32_t address, unsigned long klein_reg)
{
  _REG32(klein_reg, KLEIN_DATA_A) = address;
  _REG32(klein_reg, KLEIN_DATA_B) = 0;
  _REG32(klein_reg, KLEIN_TRIGGER) = 0x00000001;
  uint32_t temp = _REG32(klein_reg, KLEIN_DATA_C);
  _REG32(klein_reg, KLEIN_TRIGGER) = 0x00000000;
  return temp;
}

void klein_test(unsigned long klein_reg)
{
  // reset
  _REG32(klein_reg, KLEIN_TRIGGER) = 0x00010000;
  _REG32(klein_reg, KLEIN_TRIGGER) = 0x00000000;
  // cipher
  klein_write_to_address(KLEIN_ADDR_BLOCK0, 0xdeadbeef, klein_reg); // inp
  klein_write_to_address(KLEIN_ADDR_BLOCK1, 0xf000000f, klein_reg);
  klein_write_to_address(KLEIN_ADDR_KEY0, 0x12345678, klein_reg); // key
  klein_write_to_address(KLEIN_ADDR_KEY1, 0x90abcdef, klein_reg);

  klein_write_to_address(KLEIN_ADDR_CONF, 0x01, klein_reg);
  klein_write_to_address(KLEIN_ADDR_CTRL, 0x02, klein_reg);

  while(!(klein_read_to_address(KLEIN_ADDR_STATUS, klein_reg) == 0x03));

  uint32_t block_1, block_2;
  block_1 = klein_read_to_address(KLEIN_ADDR_RESULT0, klein_reg);
  block_2 = klein_read_to_address(KLEIN_ADDR_RESULT1, klein_reg);

  kprintf("\r\n----------1. Test klein with cipher and decipher\r\n");
  kputs("\rInput:    deadbeeff000000f\r\n");
  kputs("\rOutput:   1234567890abcdef\r\n");
  kprintf("\rCipher:   %lx%lx\r\n", block_1, block_2);

  // decipher
  klein_write_to_address(KLEIN_ADDR_BLOCK0, block_1, klein_reg); // inp
  klein_write_to_address(KLEIN_ADDR_BLOCK1, block_2, klein_reg);
  klein_write_to_address(KLEIN_ADDR_KEY0, 0x12345678, klein_reg); // key
  klein_write_to_address(KLEIN_ADDR_KEY1, 0x90abcdef, klein_reg);

  klein_write_to_address(KLEIN_ADDR_CONF, 0x00, klein_reg);
  klein_write_to_address(KLEIN_ADDR_CTRL, 0x01, klein_reg);

  while(!(klein_read_to_address(KLEIN_ADDR_STATUS, klein_reg) == 0x01));
  klein_write_to_address(KLEIN_ADDR_CTRL, 0x02, klein_reg);
  while(!(klein_read_to_address(KLEIN_ADDR_STATUS, klein_reg) == 0x03));

  block_1 = klein_read_to_address(KLEIN_ADDR_RESULT0, klein_reg);
  block_2 = klein_read_to_address(KLEIN_ADDR_RESULT1, klein_reg);

  kprintf("\rDecipher: %lx%lx\r\n", block_1, block_2);
}