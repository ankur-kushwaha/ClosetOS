import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../models/models.dart';
import '../theme/app_theme.dart';

class SectionHeader extends StatelessWidget {
  const SectionHeader({
    super.key,
    required this.title,
    this.subtitle,
  });

  final String title;
  final String? subtitle;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title.toUpperCase(),
            style: const TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              letterSpacing: 2,
              color: AppColors.gray400,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            title,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w300,
                  letterSpacing: -0.5,
                ),
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 4),
            Text(
              subtitle!,
              style: const TextStyle(color: AppColors.gray400, fontSize: 13),
            ),
          ],
        ],
      ),
    );
  }
}

class GarmentImage extends StatelessWidget {
  const GarmentImage({
    super.key,
    required this.path,
    this.fit = BoxFit.cover,
  });

  final String path;
  final BoxFit fit;

  @override
  Widget build(BuildContext context) {
    if (path.isEmpty) {
      return Container(
        color: AppColors.greige,
        child: const Icon(Icons.checkroom_outlined, color: AppColors.ink400),
      );
    }

    if (path.startsWith('b64://')) {
      final b64 = extractB64Payload(path);
      if (b64 == null) return _placeholder();
      return Image.memory(
        base64Decode(b64),
        fit: fit,
        gaplessPlayback: true,
        errorBuilder: (_, __, ___) => _placeholder(),
      );
    }

    if (path.startsWith('http://') || path.startsWith('https://')) {
      return Image.network(
        path,
        fit: fit,
        gaplessPlayback: true,
        errorBuilder: (_, __, ___) => _placeholder(),
        loadingBuilder: (context, child, progress) {
          if (progress == null) return child;
          return Container(
            color: AppColors.greige,
            child: const Center(
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          );
        },
      );
    }

    if (kIsWeb) {
      return _placeholder();
    }

    return Image.file(
      File(path),
      fit: fit,
      gaplessPlayback: true,
      errorBuilder: (_, __, ___) => _placeholder(),
    );
  }

  Widget _placeholder() => Container(
        color: AppColors.greige,
        child: const Icon(Icons.image_not_supported_outlined,
            color: AppColors.ink400),
      );
}

class GarmentTile extends StatelessWidget {
  const GarmentTile({
    super.key,
    required this.label,
    required this.subtitle,
    required this.imagePath,
    this.onTap,
    this.trailing,
  });

  final String label;
  final String subtitle;
  final String imagePath;
  final VoidCallback? onTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          border: Border.all(color: AppColors.gray800),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: GarmentImage(path: imagePath),
            ),
            Padding(
              padding: const EdgeInsets.all(10),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        Text(
                          subtitle,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 11,
                            color: AppColors.gray400,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (trailing != null) trailing!,
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class MinimalDivider extends StatelessWidget {
  const MinimalDivider({super.key});

  @override
  Widget build(BuildContext context) => const Divider(height: 1);
}

class OutfitGarmentsPreview extends StatelessWidget {
  const OutfitGarmentsPreview({
    super.key,
    required this.garments,
    this.height = 130.0,
  });

  final List<Garment> garments;
  final double height;

  @override
  Widget build(BuildContext context) {
    final count = garments.length;
    
    // Dynamically adjust box size based on the number of items
    final double boxSize = count <= 1
        ? height * 0.75
        : count == 2
            ? height * 0.70
            : count == 3
                ? height * 0.65
                : height * 0.58;

    return Container(
      height: height,
      width: double.infinity,
      color: AppColors.greige,
      alignment: Alignment.center,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        physics: const NeverScrollableScrollPhysics(),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: garments.map((g) {
              return Container(
                margin: const EdgeInsets.symmetric(horizontal: 6),
                width: boxSize,
                height: boxSize,
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppColors.border),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.02),
                      blurRadius: 6,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                clipBehavior: Clip.antiAlias,
                padding: const EdgeInsets.all(8),
                child: GarmentImage(
                  path: g.displayImage,
                  fit: BoxFit.contain,
                ),
              );
            }).toList(),
          ),
        ),
      ),
    );
  }
}

