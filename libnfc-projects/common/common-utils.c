#include <string.h>
#include "common-utils.h"

void print_hexdump(const unsigned char *pbtData, const size_t szData)
{
  unsigned int offsetPos = 0;
  int iLine;
  int ctr, ktr;

  if(szData < 16)
    iLine = szData;
  else
    iLine = 16;

  while(offsetPos < szData) {
    printf("\n");
    printf("%.8X ", offsetPos); //Offset

    for(ctr = 0; ctr < 16; ctr ++) {
      if(ctr == 8)
	printf(" ");
      if(ctr < iLine)
	printf("%02x ", pbtData[offsetPos + ctr]); // 16 bytes hex value
      else
	printf("   ");
    }
    printf("|");
    for(ktr = 0; ktr < iLine; ktr ++) {
      if((pbtData[offsetPos + ktr] < 32) || (pbtData[offsetPos + ktr] > 126))
	printf(".");
      else
	printf("%c", pbtData[offsetPos + ktr]);	
    }
    printf("|");
    offsetPos += iLine;
    if((szData - offsetPos) < 16)
      iLine = szData - offsetPos;
  }
  printf("\n");
}

void string_to_byteArray(char *string, unsigned char *dest, size_t *len){
  int k;
  *len = strlen(string)/2;
  for(k = 0; k < *len; k++) {
    char hexchar[3] = {0};
    memcpy(hexchar, string + (k*2), 2);
    dest[k] = (unsigned char) strtol(hexchar, NULL, 16);
  }
  
}
