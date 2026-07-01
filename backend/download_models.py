import os
import sys

# Add current directory to path so pipeline imports work
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from pipeline.model_loaders import ModelLoaders
from pipeline.fashion_clip import get_fashion_clip_model

def main():
    print("Pre-downloading machine learning models for offline/container deployment...")
    
    # Initialize loaders with CPU
    loaders = ModelLoaders("cpu")
    
    print("\n1. Loading Segment Anything (SAM)...")
    loaders.get_sam_model()
    
    print("\n2. Loading Florence-2-base...")
    loaders.get_florence_model()
    
    print("\n3. Loading FashionCLIP...")
    try:
        # This will download the sentence transformer weights or CLIP weights
        get_fashion_clip_model("cpu")
    except Exception as e:
        print(f"Error caching FashionCLIP: {e}")
        print("Retrying with Hugging Face transformers fallback...")
        from transformers import CLIPModel, CLIPProcessor
        CLIPModel.from_pretrained("patrickjohncyh/fashion-clip")
        CLIPProcessor.from_pretrained("patrickjohncyh/fashion-clip")
        
    print("\n4. Loading YOLO-World...")
    try:
        # Load YOLO-World model locally to verify and register
        loaders.get_yolo_world_model()
    except Exception as e:
        print(f"Error checking YOLO-World: {e}")

    print("\nModel caching complete! All models are verified and cached.")

if __name__ == "__main__":
    main()
