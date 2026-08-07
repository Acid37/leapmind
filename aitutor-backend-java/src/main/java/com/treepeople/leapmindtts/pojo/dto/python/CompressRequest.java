package com.treepeople.leapmindtts.pojo.dto.python;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompressRequest {
    private String contextText;
    private int maxCompressedTokens;
}
