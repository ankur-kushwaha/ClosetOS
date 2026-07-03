import os
import sys

# Add current directory to path so pipeline imports work
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from pipeline.config import YOLO_WORLD_VOCAB
from pipeline.fashion_clip import get_fashion_clip_model
from pipeline.model_assets import ensure_realesrgan_weights
from pipeline.model_loaders import ModelLoaders

def main():
    print("Pre-downloading machine learning models for offline/container deployment...")

    # Initialize loaders with CPU
    loaders = ModelLoaders("cpu")

    failures = []

    def step(label, fn):
        print(f"\n{label}...")
        try:
            fn()
        except Exception as e:
            print(f"Error caching {label}: {e}")
            failures.append(label)

    step("1. Loading Segment Anything (SAM)", loaders.get_sam_model)
    step("2. Loading Florence-2-base", loaders.get_florence_model)

    def load_fashion_clip():
        try:
            # This will download the sentence transformer weights or CLIP weights
            get_fashion_clip_model("cpu")
        except Exception as e:
            print(f"Error caching FashionCLIP via fashion-clip package: {e}")
            print("Retrying with Hugging Face transformers fallback...")
            from transformers import CLIPModel, CLIPProcessor
            CLIPModel.from_pretrained("patrickjohncyh/fashion-clip")
            CLIPProcessor.from_pretrained("patrickjohncyh/fashion-clip")

    step("3. Loading FashionCLIP", load_fashion_clip)

    def load_yolo_world():
        model = loaders.get_yolo_world_model()
        # Triggers ultralytics' lazy install of git+https://github.com/ultralytics/CLIP.git
        # if it isn't already present — must happen at build time, not on first request.
        model.set_classes(YOLO_WORLD_VOCAB)

    step("4. Loading YOLO-World", load_yolo_world)

    def load_rembg():
        # get_rembg_session() swallows its own errors and returns None on failure
        if loaders.get_rembg_session() is None:
            raise RuntimeError("rembg session could not be initialized")

    step("5. Initializing rembg cloth session", load_rembg)
    step("6. Downloading RealESRGAN weights", ensure_realesrgan_weights)

    if failures:
        print(f"\nModel caching FAILED for: {', '.join(failures)}")
        print("Failing the build so a broken image is not deployed with models missing "
              "from the cache (they would otherwise be downloaded on the first real request).")
        sys.exit(1)

    print("\nModel caching complete! All models are verified and cached.")

if __name__ == "__main__":
    main()
