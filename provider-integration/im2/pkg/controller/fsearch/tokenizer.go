package fsearch

import (
	"encoding/json"
	"fmt"
	"github.com/sugarme/tokenizer"
	"github.com/sugarme/tokenizer/pretrained"
)

func TokenizerFromData(data []byte) (*tokenizer.Tokenizer, error) {
	var config *tokenizer.Config
	err := json.Unmarshal(data, &config)
	if err != nil {
		return nil, err
	}

	model, err := pretrained.CreateModel(config)
	if err != nil {
		err := fmt.Errorf("Creating Model failed: %v", err)
		return nil, err
	}

	tk := tokenizer.NewTokenizer(model)

	// 2. Normalizer
	n, err := pretrained.CreateNormalizer(config.Normalizer)
	if err != nil {
		err = fmt.Errorf("Creating Normalizer failed: %v", err)
		return nil, err
	}
	tk.WithNormalizer(n)

	// 3. PreTokenizer
	preTok, err := pretrained.CreatePreTokenizer(config.PreTokenizer)
	if err != nil {
		err = fmt.Errorf("Creating PreTokenizer failed: %v", err)
		return nil, err
	}
	tk.WithPreTokenizer(preTok)

	// 4. PostProcessor
	postProcessor, err := pretrained.CreatePostProcessor(config.PostProcessor)
	if err != nil {
		err = fmt.Errorf("Creating PostProcessor failed: %v", err)
		return nil, err
	}
	tk.WithPostProcessor(postProcessor)

	// 5. Decoder
	decoder, err := pretrained.CreateDecoder(config.Decoder)
	if err != nil {
		err = fmt.Errorf("Creating Decoder failed: %v", err)
		return nil, err
	}
	tk.WithDecoder(decoder)

	// 6. AddedVocabulary
	specialAddedTokens, addedTokens := pretrained.CreateAddedTokens(config.AddedTokens)
	if len(specialAddedTokens) > 0 {
		tk.AddSpecialTokens(specialAddedTokens)
	}
	if len(addedTokens) > 0 {
		tk.AddTokens(addedTokens)
	}

	// 7. TruncationParams
	truncParams, err := pretrained.CreateTruncationParams(config.Truncation)
	if err != nil {
		err = fmt.Errorf("Creating TruncationParams failed: %v", err)
		return nil, err
	}
	tk.WithTruncation(truncParams)

	// 8. PaddingParams
	paddingParams, err := pretrained.CreatePaddingParams(config.Padding)
	if err != nil {
		err = fmt.Errorf("Creating PaddingParams failed: %v", err)
		return nil, err
	}
	tk.WithPadding(paddingParams)

	return tk, nil
}
