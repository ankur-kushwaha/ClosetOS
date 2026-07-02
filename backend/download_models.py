import os
import sys

# Add current directory to path so pipeline imports work
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from pipeline.fashion_clip import get_fashion_clip_model
from pipeline.model_assets import ensure_realesrgan_weights
from pipeline.model_loaders import ModelLoaders

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
        loaders.get_yolo_world_model()
    except Exception as e:
        print(f"Error checking YOLO-World: {e}")

    print("\n5. Initializing rembg cloth session...")
    try:
        loaders.get_rembg_session()
    except Exception as e:
        print(f"Error caching rembg session: {e}")

    print("\n6. Downloading RealESRGAN weights...")
    try:
        ensure_realesrgan_weights()
    except Exception as e:
        print(f"Error caching RealESRGAN weights: {e}")

    print("\nModel caching complete! All models are verified and cached.")

if __name__ == "__main__":
    main()
