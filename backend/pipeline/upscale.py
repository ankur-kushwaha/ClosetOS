"""Optional async hi-res upscaling."""

import os


def run_upscale_async(input_path: str, output_path: str):
    try:
        import cv2
        from realesrgan import RealESRGANer

        from .model_assets import ensure_realesrgan_weights

        print("Upscaling image with RealESRGAN...")
        model_path = str(ensure_realesrgan_weights())
        model = RealESRGANer(scale=4, model_path=model_path, gpu_id=None)
        img = cv2.imread(input_path, cv2.IMREAD_UNCHANGED)
        output, _ = model.enhance(img, outscale=4)
        cv2.imwrite(output_path, output)
        print(f"Upscaling complete! Saved to {output_path}")
    except Exception as e:
        print(f"RealESRGAN failed, falling back to Lanczos: {e}")
        try:
            from PIL import Image
            img = Image.open(input_path)
            w, h = img.size
            img.resize((w * 4, h * 4), Image.Resampling.LANCZOS).save(output_path)
        except Exception as ex:
            print(f"Failed fallback upscaling: {ex}")
