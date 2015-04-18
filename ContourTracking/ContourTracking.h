/*
 * Copyright (c) 2006 Intel Corporation
 * All rights reserved.
 *
 * This file is distributed under the terms in the attached INTEL-LICENSE     
 * file. If you do not find these files, copies can be found by writing to
 * Intel Research Berkeley, 2150 Shattuck Avenue, Suite 1300, Berkeley, CA, 
 * 94704.  Attention:  Intel License Inquiry.
 */

// @author David Gay

#ifndef CONTOURTRACKING_H
#define CONTOURTRACKING_H

enum {
  /* Number of readings per message. If you increase this, you may have to
     increase the message_t size. */
  NREADINGS = 2,

  /* Default sampling period. */
  DEFAULT_INTERVAL = 50,
  DEFAULT_THRESHOLD = 500,

  AM_CONTOURTRACKING = 0x93
};

typedef nx_struct contourtracking {
  nx_uint16_t version; /* Version of the interval. */
  nx_uint16_t interval; /* Samping period. */
  nx_uint16_t threshold; /* Sample threshold. */
  nx_uint16_t id; /* Mote id of sending mote. */
	nx_int64_t clock; /* timestamp of the last sample. */
  nx_uint16_t count; /* The readings are samples count * NREADINGS onwards */
  nx_uint16_t readings[NREADINGS];
	nx_uint32_t    ftsp_local_timestamp; 
	nx_uint32_t    ftsp_global_timestamp;
	nx_uint16_t    ftsp_root_id;
	nx_uint8_t     ftsp_synced;
	nx_uint8_t     ftsp_seq;
	nx_uint8_t     ftsp_table_entries;
	nx_float     	 ftsp_skew;
} contourtracking_t;

#endif
